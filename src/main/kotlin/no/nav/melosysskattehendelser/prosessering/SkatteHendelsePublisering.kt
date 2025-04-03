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
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektConsumer
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektRequest
import no.nav.melosysskattehendelser.skatt.SkatteHendelserFetcher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

private val log = KotlinLogging.logger { }

@Component
class SkatteHendelsePublisering(
    private val skatteHendelserFetcher: SkatteHendelserFetcher,
    private val personRepository: PersonRepository,
    private val skatteHendelserStatusRepository: SkatteHendelserStatusRepository,
    private val skattehendelserProducer: SkattehendelserProducer,
    private val pensjonsgivendeInntektConsumer: PensjonsgivendeInntektConsumer,
    private val metrikker: Metrikker,
    private val personFinderSelector: PersonFinderSelector,
    kafkaContainerMonitor: KafkaContainerMonitor,
) {

    private val jobMonitor = JobMonitor(
        jobName = "SkatteHendelserJob",
        stats = SkatteHendelsePubliseringStats(),
        canStart = { kafkaContainerMonitor.isKafkaContainerRunning() },
        canNotStartMessage = "kafka container er stoppet!"
    )

    private lateinit var personFinder: PersonFinder

    @Async
    fun asynkronProsesseringAvSkattHendelser(options: SkatteHendelsePubliseringOptions) {
        prosesserSkattHendelser(options)
    }

    fun prosesserSkattHendelser(options: SkatteHendelsePubliseringOptions = SkatteHendelsePubliseringOptions()) =
        jobMonitor.execute {
            personFinder = if (options.useCache) {
                personFinderSelector.find(PersonFinderType.CACHED).also {
                    personCacheSize = (it as PersonFinderCached).refresh()
                    log.info("Bruker cached person finder, cache size: $personCacheSize")
                }
            } else personFinderSelector.find(PersonFinderType.DB)

            val start = hentStartSekvensNummer()

            skatteHendelserFetcher.hentHendelser(
                startSeksvensnummer = start,
                batchDone = { sekvensnummer ->
                    oppdaterStatus(sekvensnummer)
                },
                reportStats = { stats ->
                    antallBatcher = stats.antallBatcher
                    sisteBatchSize = stats.sisteBatchSize
                    jobMonitor.importMethodMetrics(stats.metodeStats)
                }
            ).forEach { hendelse ->
                if (jobMonitor.shouldStop) return@execute
                metrikker.hendelseHentet()
                registerHendelseStats(hendelse)
                if (options.dryRun) return@forEach
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

    private fun hentStartSekvensNummer(): Long {
        val sekvensnummer = skatteHendelserStatusRepository.findById(skatteHendelserFetcher.consumerId)
            .getOrNull()?.sekvensnummer?.also { log.info { "Sekvensnummer:$it funnet i databasen" } }

        return sekvensnummer ?: skatteHendelserFetcher.startSekvensnummer
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
        jobMonitor.measureExecution("finnPersonMedTreffIGjelderPeriode") {
            personFinder.findPersonByIdent(hendelse)
        }

    fun stopProsesseringAvSkattHendelser() {
        log.info("Stopper prosessering av skattehendelser!")
        jobMonitor.stop()
    }

    fun status(periodeFilter: String): Map<String, Any?> {
        jobMonitor.stats.periodeFilter = periodeFilter
        return jobMonitor.status()
    }


    fun finnHendelser(identifikator: String): List<HendelseMedDatoForFastsetting>? {
        return jobMonitor.stats.identifikatorDuplikatToHendelse[identifikator]?.map {
            hentDatoForFastsetting(it)
        }
    }

    private fun hentDatoForFastsetting(hendelse: Hendelse): HendelseMedDatoForFastsetting =
        HendelseMedDatoForFastsetting(
            hendelse, pensjonsgivendeInntektConsumer.hentPensjonsgivendeInntekt(
                PensjonsgivendeInntektRequest(
                    navPersonident = hendelse.identifikator,
                    inntektsaar = hendelse.gjelderPeriode,
                )
            ).pensjonsgivendeInntekt.map { it.datoForFastsetting }
        )

    private fun oppdaterStatus(sekvensnummer: Long) {
        jobMonitor.stats.sisteSekvensnummer = sekvensnummer
        skatteHendelserStatusRepository.save(SkatteHendelserSekvens(skatteHendelserFetcher.consumerId, sekvensnummer))
    }
}
