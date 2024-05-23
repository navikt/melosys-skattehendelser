package no.nav.melosysskattehendelser

import mu.KotlinLogging
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.domain.SkatteHendelserSekvens
import no.nav.melosysskattehendelser.domain.SkatteHendelserStatusRepository
import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducer
import no.nav.melosysskattehendelser.skatt.SkatteHendelserFetcher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

@Component
class SkatteHendelsePublisering(
    private val skatteHendelserFetcher: SkatteHendelserFetcher,
    private val personRepository: PersonRepository,
    private val skatteHendelserStatusRepository: SkatteHendelserStatusRepository,
    private val skattehendelserProducer: SkattehendelserProducer
) {
    private val log = KotlinLogging.logger { }

    @Async
    fun asynkronProsesseringAvSkattHendelser() {
        prosesserSkattHendelser()
    }

    @Synchronized
    fun prosesserSkattHendelser() {
        val start = skatteHendelserStatusRepository.findById(skatteHendelserFetcher.consumerId)
            .getOrNull()?.sekvensnummer ?: skatteHendelserFetcher.startSekvensnummer

        skatteHendelserFetcher.hentHendelser(
            startSeksvensnummer = start,
            batchDone = { sekvensnummer -> oppdaterStatus(sekvensnummer) }
        ).forEach { hendelse ->
            personRepository.findPersonByIdent(hendelse.identifikator)?.let { person ->
                log.info { "Fant person ${person.ident} for hendelse ${hendelse.sekvensnummer}" }
                // skal vi publisere dette direkte? f.eks sekvensnummer er har jo ingen verdi for melosys
                skattehendelserProducer.publiserMelding(hendelse)
                oppdaterStatus(hendelse.sekvensnummer + 1)
            }
        }
    }

    private fun oppdaterStatus(sekvensnummer: Long) {
        skatteHendelserStatusRepository.save(SkatteHendelserSekvens(skatteHendelserFetcher.consumerId, sekvensnummer))
    }
}