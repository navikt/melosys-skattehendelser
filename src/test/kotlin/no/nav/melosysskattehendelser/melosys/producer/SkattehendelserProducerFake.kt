package no.nav.melosysskattehendelser.melosys.producer

import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse

class SkattehendelserProducerFake : SkattehendelserProducer {
    val hendelser = mutableListOf<MelosysSkatteHendelse>()

    fun reset() = apply {
        hendelser.clear()
    }

    override fun publiserMelding(hendelse: MelosysSkatteHendelse) {
        hendelser.add(hendelse)
    }
}