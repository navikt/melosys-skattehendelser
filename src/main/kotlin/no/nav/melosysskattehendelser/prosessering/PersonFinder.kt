package no.nav.melosysskattehendelser.prosessering

import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.skatt.Hendelse

fun interface PersonFinder {
    fun findPersonByIdent(hendelse: Hendelse): Person?
}