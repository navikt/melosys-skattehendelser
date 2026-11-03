package no.nav.melosysskattehendelser.melosys.producer

import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse

class SkattehendelserProducerFake : SkattehendelserProducer {
    val hendelser = mutableListOf<MelosysSkatteHendelse>()
    val personIder = mutableListOf<Long?>()
    override var dryRun: Boolean = false

    fun reset() = apply {
        hendelser.clear()
        personIder.clear()
        dryRun = false
    }

    override fun publiserMelding(hendelse: MelosysSkatteHendelse, personId: Long?) {
        hendelser.add(hendelse)
        personIder.add(personId)
    }
}