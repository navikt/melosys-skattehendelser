package no.nav.melosysskattehendelser.prosessering

import org.springframework.stereotype.Component

@Component
class PersonFinderSelector(
    private val personFinders: Map<String, PersonFinder>
) {
    fun find(type: PersonFinderType): PersonFinder =
        personFinders[type.name] ?: error("No PersonFinder for type '$type'")

}
