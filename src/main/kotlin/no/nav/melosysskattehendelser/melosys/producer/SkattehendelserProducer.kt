package no.nav.melosysskattehendelser.melosys.producer

import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse

fun interface SkattehendelserProducer {
    fun publiserMelding(hendelse: MelosysSkatteHendelse)
}