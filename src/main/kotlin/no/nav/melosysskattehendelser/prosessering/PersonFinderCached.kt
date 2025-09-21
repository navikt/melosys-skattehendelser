package no.nav.melosysskattehendelser.prosessering

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.metrics.Measured
import no.nav.melosysskattehendelser.skatt.Hendelse
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger { }

@Component("CACHED")
class PersonFinderCached(
    private val personRepository: PersonRepository
) : PersonFinder {

    private val cache = ConcurrentHashMap<String, Person>()

    fun refresh(): Int {
        val newData = personRepository.findAll()
        cache.clear()
        newData.forEach { person ->
            cache[person.ident] = person
        }
        log.info("Person cache refreshed with ${cache.size} entries")
        return cache.size
    }

    @Measured
    override fun findPersonByIdent(hendelse: Hendelse): Person? =
        cache[hendelse.identifikator]?.takeIf {
            it.harTreffIPeriode(hendelse.gjelderPeriodeSomÅr())
        }?.let {
            personRepository.findPersonByIdent(hendelse.identifikator)
        }
}