package no.nav.melosysskattehendelser.melosys.consumer

import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.domain.PersonRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PersonTestService(
    private val personRepository: PersonRepository
) {
    @Transactional
    fun savePerson(person: Person) {
        personRepository.save(person)
    }
}
