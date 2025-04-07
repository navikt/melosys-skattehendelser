package no.nav.melosysskattehendelser.prosessering

import mu.KotlinLogging
import no.nav.melosysskattehendelser.domain.PensjonsgivendeInntekt
import no.nav.melosysskattehendelser.domain.PensjonsgivendeInntektRepository
import no.nav.melosysskattehendelser.domain.Periode
import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.domain.SkatteHendelserSekvens
import no.nav.melosysskattehendelser.domain.SkatteHendelserStatusRepository
import no.nav.melosysskattehendelser.melosys.consumer.KafkaContainerMonitor
import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducer
import no.nav.melosysskattehendelser.melosys.toMelosysSkatteHendelse
import no.nav.melosysskattehendelser.metrics.MeasuredMetricsProvider
import no.nav.melosysskattehendelser.metrics.Metrikker
import no.nav.melosysskattehendelser.skatt.Hendelse
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektConsumer
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektRequest
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektResponse
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
    private val pensjonsgivendeInntektRepository: PensjonsgivendeInntektRepository,
    private val skattehendelserProducer: SkattehendelserProducer,
    private val pensjonsgivendeInntektConsumer: PensjonsgivendeInntektConsumer,
    private val metrikker: Metrikker,
    private val personFinderSelector: PersonFinderSelector,
    kafkaContainerMonitor: KafkaContainerMonitor,
    measuredMetricsProvider: MeasuredMetricsProvider
) {

    private val jobMonitor = JobMonitor(
        jobName = "SkatteHendelserJob",
        stats = SkatteHendelsePubliseringStats(),
        canStart = { kafkaContainerMonitor.isKafkaContainerRunning() },
        canNotStartMessage = "kafka container er stoppet!",
        metricsProvider = measuredMetricsProvider
    )

    @Async
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
                        if (!options.dryRun) {
                            sjekkInntektOgPubliserOmNy(hendelse, person)
                        }
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

    private fun sjekkInntektOgPubliserOmNy(hendelse: Hendelse, person: Person) {
        val inntekt = hentPensjonsgivendeInntekt(hendelse, person)
        if (inntekt.inntektsaar != hendelse.gjelderPeriode) {
            log.warn { "inntektsaar ${inntekt.inntektsaar} er ikke lik hendelse.gjelderPeriode ${hendelse.gjelderPeriode}" }
        }
        val periode = person.perioder.find { it.harTreff(hendelse.gjelderPeriodeSomÅr()) }
            ?: throw IllegalArgumentException("Fant ikke periode for ${hendelse.gjelderPeriodeSomÅr()} for person ${person.ident}")

        if (harNyInntekt(inntekt, periode)) {
            inntekt.tilDomain(periode).also {
                pensjonsgivendeInntektRepository.save(it)
                log.info("Lagrer pensjonsgivende inntekt for person ${person.ident} for sekvensnummer ${hendelse.sekvensnummer}")
            }
            publiserMelding(hendelse, person)
        }
    }

    fun harNyInntekt(ny: PensjonsgivendeInntektResponse, periode: Periode): Boolean {
        val eksisterende: List<PensjonsgivendeInntekt> = pensjonsgivendeInntektRepository.findByPeriode(periode)
        if (eksisterende.isEmpty()) return true

        eksisterende.find { it.historiskInntekt == ny }?.let {
            log.warn("Fant duplikat inntekt for person ${periode.id} pensjonsgivendeInntektID: ${it.id}")
            it.duplikater++
            pensjonsgivendeInntektRepository.save(it)
            return false
        }
        return true
    }

    private fun hentPensjonsgivendeInntekt(hendelse: Hendelse, person: Person): PensjonsgivendeInntektResponse =
        pensjonsgivendeInntektConsumer.hentPensjonsgivendeInntekt(
            PensjonsgivendeInntektRequest(
                inntektsaar = hendelse.gjelderPeriode,
                navPersonident = person.ident
            )
        )

    private fun PensjonsgivendeInntektResponse.tilDomain(periode: Periode): PensjonsgivendeInntekt =
        PensjonsgivendeInntekt(
            periode = periode,
            historiskInntekt = this
        )

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


    fun finnHendelser(identifikator: String): List<HendelseMedDatoForFastsetting>? =
        jobMonitor.stats.identifikatorDuplikatToHendelse[identifikator]?.map {
            hentDatoForFastsetting(it)
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
