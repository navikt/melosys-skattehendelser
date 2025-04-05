package no.nav.melosysskattehendelser.prosessering

import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.metrics.Measured
import no.nav.melosysskattehendelser.skatt.Hendelse
import org.springframework.stereotype.Component

@Component("DB")
class PersonFinderDB(private val personRepository: PersonRepository) : PersonFinder {
    @Measured
    override fun findPersonByIdent(hendelse: Hendelse): Person? =
        personRepository.findPersonByIdent(hendelse.identifikator)?.takeIf { person ->
            person.harTreffIPeriode(hendelse.gjelderPeriodeSomÅr())
        }
}