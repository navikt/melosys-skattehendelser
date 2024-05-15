package no.nav.melosysskattehendelser

import mu.KotlinLogging
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.domain.SkatteHendelserStatus
import no.nav.melosysskattehendelser.domain.SkatteHendelserStatusRepository
import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducer
import no.nav.melosysskattehendelser.skatt.SkattHendelserFetcher
import org.springframework.stereotype.Component

@Component
class SkattHendelseProsessering(
    private val skattHendelserFetcher: SkattHendelserFetcher,
    private val personRepository: PersonRepository,
    private val skatteHendelserStatusRepository: SkatteHendelserStatusRepository,
    private val skattehendelserProducer: SkattehendelserProducer
) {
    private val log = KotlinLogging.logger { }

    //@Async
    //@Synchronized
    fun prosesserHendelser() {
        val start = skatteHendelserStatusRepository.findById(skattHendelserFetcher.consumerId)
            .orElse(null)?.sekvensnummer ?: skattHendelserFetcher.startSekvensnummer

        skattHendelserFetcher.hentHendelser(start).forEach {
            personRepository.findPersonByIdent(it.identifikator)?.let { person ->
                log.info { "Fant person ${person.ident} for hendelse ${it.sekvensnummer}" }
                // skal vi publisere dette direkte? f.eks sekvensnummer er har jo ingen verdi for meloys
                skattehendelserProducer.publiserMelding(it)
            }
            skatteHendelserStatusRepository.save(SkatteHendelserStatus(skattHendelserFetcher.consumerId, it.sekvensnummer + 1))
        }
    }
}