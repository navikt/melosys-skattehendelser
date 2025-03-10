package no.nav.melosysskattehendelser.prosessering

import mu.KotlinLogging
import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.domain.SkatteHendelserSekvens
import no.nav.melosysskattehendelser.domain.SkatteHendelserStatusRepository
import no.nav.melosysskattehendelser.melosys.consumer.KafkaContainerMonitor
import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducer
import no.nav.melosysskattehendelser.melosys.toMelosysSkatteHendelse
import no.nav.melosysskattehendelser.metrics.Metrikker
import no.nav.melosysskattehendelser.skatt.Hendelse
import no.nav.melosysskattehendelser.skatt.SkatteHendelserFetcher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

@Component
class SkatteHendelsePublisering(
    private val skatteHendelserFetcher: SkatteHendelserFetcher,
    private val personRepository: PersonRepository,
    private val skatteHendelserStatusRepository: SkatteHendelserStatusRepository,
    private val skattehendelserProducer: SkattehendelserProducer,
    private val metrikker: Metrikker,
    kafkaContainerMonitor: KafkaContainerMonitor
) {
    private val log = KotlinLogging.logger { }

    private val job = Job(
        jobName = "SkatteHendelserJob",
        stats = Stats(),
        canStart = { kafkaContainerMonitor.isKafkaContainerRunning() },
        canNotStartMessage ="kafka container er stoppet!"
    )

    @Async
    fun asynkronProsesseringAvSkattHendelser() {
        prosesserSkattHendelser()
    }

    fun prosesserSkattHendelser() = job.execute {
        val start = skatteHendelserStatusRepository.findById(skatteHendelserFetcher.consumerId)
            .getOrNull()?.sekvensnummer ?: skatteHendelserFetcher.startSekvensnummer

        skatteHendelserFetcher.hentHendelser(
            startSeksvensnummer = start,
            batchDone = { sekvensnummer ->
                oppdaterStatus(sekvensnummer)
            },
            reportStats = { stats ->
                antallBatcher = stats.antallBatcher
                sisteBatchSize = stats.sisteBatchSize
            }
        ).forEach { hendelse ->
            if (job.shouldStop) return@forEach
            metrikker.hendelseHentet()
            totaltAntallHendelser++
            finnPersonMedTreffIGjelderPeriode(hendelse)?.let { person ->
                metrikker.personFunnet()
                personerFunnet++
                log.info("Fant person ${person.ident} for sekvensnummer ${hendelse.sekvensnummer}")
                val sekvensHistorikk = person.hentEllerLagSekvensHistorikk(hendelse.sekvensnummer)
                if (sekvensHistorikk.erNyHendelse()) {
                    publiserMelding(hendelse, person)
                } else {
                    metrikker.duplikatHendelse()
                    log.warn("Hendelse med ${hendelse.sekvensnummer} er allerede kjørt ${sekvensHistorikk.antall} ganger for person ${person.ident}")
                }

                personRepository.save(person)
                oppdaterStatus(hendelse.sekvensnummer + 1)
            }
        }
    }

    private fun publiserMelding(hendelse: Hendelse, person: Person) {
        try {
            skattehendelserProducer.publiserMelding(hendelse.toMelosysSkatteHendelse())
            metrikker.hendelsePublisert()
        } catch (e: Exception) {
            metrikker.publiseringFeilet()
            log.error(e) {
                "Feil ved publisering av melding for hendelse ${hendelse.sekvensnummer}" +
                        " gjelderPeriode:${hendelse.gjelderPeriode} personID: ${person.id}"
            }
            throw e
        }
    }

    private fun finnPersonMedTreffIGjelderPeriode(hendelse: Hendelse): Person? =
        personRepository.findPersonByIdent(hendelse.identifikator)?.takeIf { person ->
            person.harTreffIPeriode(hendelse.gjelderPeriodeSomÅr())
        }

    fun stopProsesseringAvSkattHendelser() {
        log.info("Stopper prosessering av skattehendelser!")
        job.stop()
    }

    fun status() = job.status()

    private fun oppdaterStatus(sekvensnummer: Long) {
        job.stats.sisteSekvensnummer = sekvensnummer
        skatteHendelserStatusRepository.save(SkatteHendelserSekvens(skatteHendelserFetcher.consumerId, sekvensnummer))
    }

    private data class Stats(
        @Volatile var totaltAntallHendelser: Int = 0,
        @Volatile var personerFunnet: Int = 0,
        @Volatile var sisteSekvensnummer: Long = 0,
        @Volatile var antallBatcher: Int = 0,
        @Volatile var sisteBatchSize: Int = 0
    ) : Job.Stats {
        override fun reset() {
            totaltAntallHendelser = 0
            personerFunnet = 0
            sisteSekvensnummer = 0
            antallBatcher = 0
            sisteBatchSize = 0
        }

        override fun asMap() = mapOf(
            "totaltAntallHendelser" to totaltAntallHendelser,
            "personerFunnet" to personerFunnet,
            "sisteSekvensnummer" to sisteSekvensnummer,
            "antallBatcher" to antallBatcher,
            "sisteBatchSize" to sisteBatchSize,
        )
    }
}