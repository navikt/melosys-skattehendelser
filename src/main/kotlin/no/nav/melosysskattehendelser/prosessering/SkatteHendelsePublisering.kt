package no.nav.melosysskattehendelser.prosessering

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import mu.KotlinLogging
import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.domain.SkatteHendelserSekvens
import no.nav.melosysskattehendelser.domain.SkatteHendelserStatusRepository
import no.nav.melosysskattehendelser.melosys.consumer.KafkaContainerMonitor
import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducer
import no.nav.melosysskattehendelser.melosys.toMelosysSkatteHendelse
import no.nav.melosysskattehendelser.metrics.Measured
import no.nav.melosysskattehendelser.metrics.Metrikker
import no.nav.melosysskattehendelser.skatt.Hendelse
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektConsumer
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektRequest
import no.nav.melosysskattehendelser.skatt.SkatteHendelserFetcher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
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
    val meterRegistry: MeterRegistry,
) {

    private val jobMonitor = JobMonitor(
        jobName = "SkatteHendelserJob",
        stats = SkatteHendelsePubliseringStats(),
        canStart = { kafkaContainerMonitor.isKafkaContainerRunning() },
        canNotStartMessage = "kafka container er stoppet!"
    )

    @Async
    @Measured
    fun asynkronProsesseringAvSkattHendelser(options: SkatteHendelsePubliseringOptions) {
        prosesserSkattHendelser(options)
    }

    fun prosesserSkattHendelser(options: SkatteHendelsePubliseringOptions = SkatteHendelsePubliseringOptions()) =
        jobMonitor.execute {
            val personFinder = finderFromOptions(options)

            val start = hentStartSekvensNummer()

            skatteHendelserFetcher.hentHendelser(
                startSeksvensnummer = start,
                batchDone = ::oppdaterStatus,
                reportStats = { stats ->
                    antallBatcher = stats.antallBatcher
                    sisteBatchSize = stats.sisteBatchSize
                    jobMonitor.importMethodMetrics(stats.metodeStats)
                }
            ).takeWhile { !jobMonitor.shouldStop }.forEach { hendelse ->
                metrikker.hendelseHentet()
                personFinder.findPersonByIdent(hendelse)?.let { person ->
                    registerHendelseStats(hendelse, person.ident)
                    metrikker.personFunnet()
                    personerFunnet++

                    log.info("Fant person ${person.ident} for sekvensnummer ${hendelse.sekvensnummer}")

                    val sekvensHistorikk = person.hentEllerLagSekvensHistorikk(hendelse.sekvensnummer)
                    if (sekvensHistorikk.erNyHendelse()) {
                        if (!options.dryRun) publiserMelding(hendelse, person)
                    } else {
                        metrikker.duplikatHendelse()
                        log.warn("Hendelse med ${hendelse.sekvensnummer} er allerede kjørt ${sekvensHistorikk.antall} ganger for person ${person.ident}")
                    }

                    if (!options.dryRun) {
                        personRepository.save(person)
                        oppdaterStatus(hendelse.sekvensnummer + 1)
                    }
                } ?: registerHendelseStats(hendelse)
            }
        }

    private fun SkatteHendelsePubliseringStats.finderFromOptions(options: SkatteHendelsePubliseringOptions): PersonFinder =
        personFinderSelector.find(
            if (options.useCache) PersonFinderType.CACHED else PersonFinderType.DB
        ).also {
            if (options.useCache) {
                personCacheSize = (it as PersonFinderCached).refresh()
                log.info("Bruker cached person finder, cache size: $personCacheSize")
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

    fun stopProsesseringAvSkattHendelser() {
        log.info("Stopper prosessering av skattehendelser!")
        jobMonitor.stop()
    }

    fun status(periodeFilter: String, kunIdentMatch: Boolean): Map<String, Any?> {
        jobMonitor.stats.periodeFilter = periodeFilter
        jobMonitor.stats.kunIdentMatch = kunIdentMatch
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

    private data class MethodTimingStatus(
        val method: String,
        val count: Long,
        val totalTimeMs: Double,
        val maxTimeMs: Double
    )

    private inner class SkatteHendelsePubliseringStats(
        @Volatile var totaltAntallHendelser: Int = 0,
        @Volatile var personerFunnet: Int = 0,
        @Volatile var sisteSekvensnummer: Long = 0,
        @Volatile var antallBatcher: Int = 0,
        @Volatile var sisteBatchSize: Int = 0,
        @Volatile var personCacheSize: Int = 0,
        val gjelderPeriodeToCount: ConcurrentHashMap<String, Int> = ConcurrentHashMap(),
        val registreringstidspunktToCount: ConcurrentHashMap<String, Int> = ConcurrentHashMap(),
        val hendelsetypeToCount: ConcurrentHashMap<String, Int> = ConcurrentHashMap(),
        val identifikatorDuplikatToHendelse: ConcurrentHashMap<String, MutableList<Hendelse>> = ConcurrentHashMap(),
        val skatteHendelseIdentMatch: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap()),
        var periodeFilter: String? = "2024",
        var kunIdentMatch: Boolean = false,
    ) : JobMonitor.Stats {
        fun registerHendelseStats(hendelse: Hendelse, ident: String? = null) {
            totaltAntallHendelser++
            if (ident != null) skatteHendelseIdentMatch.add(ident)
            gjelderPeriodeToCount.incrementCount(hendelse.gjelderPeriode)
            registreringstidspunktToCount.incrementCount(hendelse.registreringstidspunktAsYearMonth())
            val hendelseList = identifikatorDuplikatToHendelse.getOrDefault(hendelse.identifikator, mutableListOf())
            hendelseList.add(hendelse)
            identifikatorDuplikatToHendelse[hendelse.identifikator] = hendelseList
        }

        fun measured(): List<MethodTimingStatus> {
            return meterRegistry["method.execution.time"].meters().mapNotNull { meter ->
                val timer = meter as? Timer ?: return@mapNotNull null
                val method = meter.id.getTag("method") ?: return@mapNotNull null
                MethodTimingStatus(
                    method = method,
                    count = timer.count(),
                    totalTimeMs = timer.totalTime(TimeUnit.MILLISECONDS),
                    maxTimeMs = timer.max(TimeUnit.MILLISECONDS)
                )
            }.sortedByDescending { it.maxTimeMs } // Optional: sort by slowness
        }

        override fun reset() {
            totaltAntallHendelser = 0
            personerFunnet = 0
            sisteSekvensnummer = 0
            antallBatcher = 0
            sisteBatchSize = 0
            personCacheSize = 0
            gjelderPeriodeToCount.clear()
            registreringstidspunktToCount.clear()
            hendelsetypeToCount.clear()
            identifikatorDuplikatToHendelse.clear()
        }

        override fun asMap(): Map<String, Any> {
            val identifikatorsWithMoreThanOnePeriode = identifikatorDuplikatToHendelse
                .asSequence()
                .filter { !kunIdentMatch || it.key in skatteHendelseIdentMatch  }
                .map { (identifikator, hendelser) ->
                    val count = hendelser.count { it.gjelderPeriode == periodeFilter }
                    Triple(identifikator, hendelser, count)
                }
                .filter { (_, _, count) -> count > 1 }
                .sortedByDescending { (_, _, count) -> count }
                .toList()

            return mapOf(
                "measured" to measured(),
                "personCache" to personCacheSize,
                "totaltAntallHendelser" to totaltAntallHendelser,
                "personerFunnet" to personerFunnet,
                "sisteSekvensnummer" to sisteSekvensnummer,
                "antallBatcher" to antallBatcher,
                "sisteBatchSize" to sisteBatchSize,
                "identifikatorDuplikatCount" to identifikatorDuplikatToHendelse
                    .filter { it.value.size > 1 }
                    .filter { !kunIdentMatch || it.key in skatteHendelseIdentMatch  }
                    .size,
                "hendelsetypeToCount" to hendelsetypeToCount,
                "gjelderPeriodeToCount" to gjelderPeriodeToCount,
                "registreringstidspunktToCount" to registreringstidspunktToCount,

                "identifikatorToCount" to identifikatorDuplikatToHendelse
                    .filter { !kunIdentMatch || it.key in skatteHendelseIdentMatch  }
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
                    .filter { !kunIdentMatch || it.key in skatteHendelseIdentMatch  }
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
}
