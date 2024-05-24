package no.nav.melosysskattehendelser

import mu.KotlinLogging
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.domain.SkatteHendelserSekvens
import no.nav.melosysskattehendelser.domain.SkatteHendelserStatusRepository
import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducer
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
    private val status: Status = Status(0, 0)

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
                batchDone = { sekvensnummer -> oppdaterStatus(sekvensnummer) },
            ).forEach { hendelse ->
                if (stop) return@run
                totaltAntallHendelser++
                personRepository.findPersonByIdent(hendelse.identifikator)?.let { person ->
                    personerFunnet++
                    log.info { "Fant person ${person.ident} for hendelse ${hendelse.sekvensnummer}" }
                    // skal vi publisere dette direkte? f.eks sekvensnummer er har jo ingen verdi for melosys
                    skattehendelserProducer.publiserMelding(hendelse)
                    oppdaterStatus(hendelse.sekvensnummer + 1)
                }
            }
        }
    }

    fun stopProsesseringAvSkattHendelser() {
        log.info("Stopper prosessering av skatt hendelser!")
        status.stop = true
    }

    fun status() = status.status()

    private fun oppdaterStatus(sekvensnummer: Long) {
        skatteHendelserStatusRepository.save(SkatteHendelserSekvens(skatteHendelserFetcher.consumerId, sekvensnummer))
    }

    class Status(
        @Volatile var totaltAntallHendelser: Int,
        @Volatile var personerFunnet: Int,
        @Volatile var isRunning: Boolean = false,
        @Volatile var startedAt: LocalDateTime = LocalDateTime.MIN,
        @Volatile var stop: Boolean = false
    ) {
        fun run(block: Status.() -> Unit) {
            totaltAntallHendelser = 0
            personerFunnet = 0
            isRunning = true
            startedAt = LocalDateTime.now()
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
            "personerFunnet" to personerFunnet
        )

    }
}