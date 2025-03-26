package no.nav.melosysskattehendelser.prosessering

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
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
    kafkaContainerMonitor: KafkaContainerMonitor,
) {

    private val jobMonitor = JobMonitor(
        jobName = "SkatteHendelserJob",
        stats = Stats(),
        canStart = { kafkaContainerMonitor.isKafkaContainerRunning() },
        canNotStartMessage = "kafka container er stoppet!"
    )

    @Async
    fun asynkronProsesseringAvSkattHendelser(options: Options) {
        prosesserSkattHendelser(options)
    }

    fun prosesserSkattHendelser(options: Options = Options()) = jobMonitor.execute {
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
            personRepository.findPersonByIdent(hendelse.identifikator)?.takeIf { person ->
                person.harTreffIPeriode(hendelse.gjelderPeriodeSomÅr())
            }
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

    private data class Stats(
        @Volatile var totaltAntallHendelser: Int = 0,
        @Volatile var personerFunnet: Int = 0,
        @Volatile var sisteSekvensnummer: Long = 0,
        @Volatile var antallBatcher: Int = 0,
        @Volatile var sisteBatchSize: Int = 0,
        val gjelderPeriodeToCount: ConcurrentHashMap<String, Int> = ConcurrentHashMap(),
        val registreringstidspunktToCount: ConcurrentHashMap<String, Int> = ConcurrentHashMap(),
        val hendelsetypeToCount: ConcurrentHashMap<String, Int> = ConcurrentHashMap(),
        val identifikatorDuplikatToHendelse: ConcurrentHashMap<String, MutableList<Hendelse>> = ConcurrentHashMap(),
        var periodeFilter: String? = "2024"
    ) : JobMonitor.Stats {
        fun registerHendelseStats(hendelse: Hendelse) {
            totaltAntallHendelser++
            gjelderPeriodeToCount.incrementCount(hendelse.gjelderPeriode)
            registreringstidspunktToCount.incrementCount(hendelse.registreringstidspunktAsYearMonth())
            val hendelseList = identifikatorDuplikatToHendelse.getOrDefault(hendelse.identifikator, mutableListOf())
            hendelseList.add(hendelse)
            identifikatorDuplikatToHendelse[hendelse.identifikator] = hendelseList
        }


        override fun reset() {
            totaltAntallHendelser = 0
            personerFunnet = 0
            sisteSekvensnummer = 0
            antallBatcher = 0
            sisteBatchSize = 0
            gjelderPeriodeToCount.clear()
            registreringstidspunktToCount.clear()
            hendelsetypeToCount.clear()
        }

        override fun asMap(): Map<String, Any> {
            val identifikatorsWithMoreThanOnePeriode = identifikatorDuplikatToHendelse
                .asSequence()
                .map { (identifikator, hendelser) ->
                    val count = hendelser.count { it.gjelderPeriode == periodeFilter }
                    Triple(identifikator, hendelser, count)
                }
                .filter { (_, _, count) -> count > 1 }
                .sortedByDescending { (_, _, count) -> count }
                .toList()

            return mapOf(
                "totaltAntallHendelser" to totaltAntallHendelser,
                "personerFunnet" to personerFunnet,
                "sisteSekvensnummer" to sisteSekvensnummer,
                "antallBatcher" to antallBatcher,
                "sisteBatchSize" to sisteBatchSize,
                "identifikatorDuplikatCount" to identifikatorDuplikatToHendelse.size,
                "hendelsetypeToCount" to hendelsetypeToCount,
                "gjelderPeriodeToCount" to gjelderPeriodeToCount,
                "registreringstidspunktToCount" to registreringstidspunktToCount,

                "identifikatorToCount" to identifikatorDuplikatToHendelse
                    .filter { it.value.size > 1 }
                    .entries
                    .sortedByDescending { it.value.size }
                    .take(100)
                    .associate { it.key to it.value.size },

                "moreThanOne${periodeFilter}PeriodeCount" to identifikatorsWithMoreThanOnePeriode.size,

                "identifikatorToMoreThanOne${periodeFilter}PeriodeCount" to identifikatorsWithMoreThanOnePeriode
                    .take(100)
                    .associate { (identifikator, _, count) -> identifikator to count },

                "identifikatorToHendelse${periodeFilter}Periode" to identifikatorDuplikatToHendelse
                    .asSequence()
                    .map { (identifikator, hendelser) ->
                        identifikator to hendelser.filter { it.gjelderPeriode == periodeFilter }
                    }
                    .filter { (_, filteredHendelser) -> filteredHendelser.size > 1 }
                    .sortedByDescending { (_, filteredHendelser) -> filteredHendelser.size }
                    .take(10)
                    .associate { (key, filteredHendelser) ->
                        key to filteredHendelser.map { hendelse ->
                            mapOf(
                                "sekvensnummer" to hendelse.sekvensnummer,
                                "gjelderPeriode" to hendelse.gjelderPeriode,
                                "registreringstidspunkt" to hendelse.registreringstidspunkt,
                            )
                        }
                    }
            )
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Options(
        val dryRun: Boolean = false,
        val filterSaksnummer: String? = null
    ) {
        companion object {
            fun av(environment: Environment): Options {
                val naisClusterName = environment.getProperty(NAIS_CLUSTER_NAME)
                val isProd: Boolean = naisClusterName?.contains("prod") == true
                log.info { "NAIS_CLUSTER_NAME=$naisClusterName, isProd=$isProd, dryRun:${isProd}" }

                return Options(
                    dryRun = isProd
                )
            }
        }
    }

    companion object {
        const val NAIS_CLUSTER_NAME = "NAIS_CLUSTER_NAME"
    }
}

data class HendelseMedDatoForFastsetting(
    val hendelse: Hendelse,
    val datoForFastsetting: List<String>
)
