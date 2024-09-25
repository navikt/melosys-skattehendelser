package no.nav.melosysskattehendelser

import mu.KotlinLogging
import no.nav.melosysskattehendelser.domain.*
import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducer
import no.nav.melosysskattehendelser.melosys.toMelosysSkatteHendelse
import no.nav.melosysskattehendelser.skatt.Hendelse
import no.nav.melosysskattehendelser.skatt.SkatteHendelserFetcher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

@Component
class SkatteHendelsePublisering(
    private val skatteHendelserFetcher: SkatteHendelserFetcher,
    private val personRepository: PersonRepository,
    private val skatteHendelserStatusRepository: SkatteHendelserStatusRepository,
    private val skattehendelserProducer: SkattehendelserProducer
) {
    private val log = KotlinLogging.logger { }
    private val status: Status = Status()

    @Async
    fun asynkronProsesseringAvSkattHendelser() {
        prosesserSkattHendelser()
    }

    fun prosesserSkattHendelser() {
        if (status.isRunning) {
            log.warn("Prosessering av skatt hendelser er allerede i gang!")
        }
        status.run {
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
                if (stop) return@run
                totaltAntallHendelser++
                finnPersonMedTreffIGjelderPeriode(hendelse)?.let { person ->
                    personerFunnet++
                    log.info("Fant person ${person.ident} for sekvensnummer ${hendelse.sekvensnummer}")
                    val sekvensHistorikk = person.hendelse(hendelse.sekvensnummer)
                    if (sekvensHistorikk.antall == 0) {
                        skattehendelserProducer.publiserMelding(hendelse.toMelosysSkatteHendelse())
                    } else {
                        log.warn("Hendelse med ${hendelse.sekvensnummer} er allerede kjørt ${sekvensHistorikk.antall} ganger for person ${person.ident}")
                    }

                    personRepository.save(person)
                    oppdaterStatus(hendelse.sekvensnummer + 1)
                }
            }
        }
    }

    private fun finnPersonMedTreffIGjelderPeriode(hendelse: Hendelse): Person? =
        personRepository.findPersonByIdent(hendelse.identifikator)?.takeIf { person ->
            person.harTreffIPeriode(hendelse.gjelderPeriodeSomÅr())
        }

    fun stopProsesseringAvSkattHendelser() {
        log.info("Stopper prosessering av skatt hendelser!")
        status.stop = true
    }

    fun status() = status.status()

    private fun oppdaterStatus(sekvensnummer: Long) {
        status.sisteSekvensnummer = sekvensnummer
        skatteHendelserStatusRepository.save(SkatteHendelserSekvens(skatteHendelserFetcher.consumerId, sekvensnummer))
    }

    class Status(
        @Volatile var totaltAntallHendelser: Int = 0,
        @Volatile var personerFunnet: Int = 0,
        @Volatile var isRunning: Boolean = false,
        @Volatile var startedAt: LocalDateTime = LocalDateTime.MIN,
        @Volatile var stop: Boolean = false,
        @Volatile var sisteSekvensnummer: Long = 0,
        @Volatile var antallBatcher: Int = 0,
        @Volatile var sisteBatchSize: Int = 0
    ) {
        fun run(block: Status.() -> Unit) {
            totaltAntallHendelser = 0
            personerFunnet = 0
            isRunning = true
            startedAt = LocalDateTime.now()
            sisteSekvensnummer = 0
            antallBatcher = 0
            sisteBatchSize = 0
            try {
                this.block()
            } finally {
                isRunning = false
                stop = false
            }
        }

        fun status(): Map<String, Any> = mapOf(
            "isRunning" to isRunning,
            "startedAt" to startedAt,
            "totaltAntallHendelser" to totaltAntallHendelser,
            "antallBatcher" to antallBatcher,
            "sisteBatchSize" to sisteBatchSize,
            "sisteSekvensnummer" to sisteSekvensnummer,
            "personerFunnet" to personerFunnet
        )
    }
}