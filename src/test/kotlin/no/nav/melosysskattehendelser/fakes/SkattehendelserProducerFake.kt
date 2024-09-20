package no.nav.melosysskattehendelser.fakes

import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse
import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducer

class SkattehendelserProducerFake : SkattehendelserProducer {
    val hendelser = mutableListOf<MelosysSkatteHendelse>()

    fun reset() = apply {
        hendelser.clear()
    }

    override fun publiserMelding(hendelse: MelosysSkatteHendelse) {
        hendelser.add(hendelse)
    }
}