package no.nav.melosysskattehendelser.domain

import org.springframework.data.repository.CrudRepository

interface PersonRepository : CrudRepository<Person, Long> {

}