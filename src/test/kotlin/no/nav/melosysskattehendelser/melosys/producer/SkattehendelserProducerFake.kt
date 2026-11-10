package no.nav.melosysskattehendelser.melosys.producer

import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse

class SkattehendelserProducerFake : SkattehendelserProducer {
    val hendelser = mutableListOf<MelosysSkatteHendelse>()
    val personIder = mutableListOf<Long?>()
    var feilVedNesteSending: Exception? = null

    fun reset() = apply {
        hendelser.clear()
        personIder.clear()
        feilVedNesteSending = null
    }

    override fun publiserMelding(hendelse: MelosysSkatteHendelse, personId: Long?) {
        feilVedNesteSending?.let {
            feilVedNesteSending = null
            throw it
        }
        hendelser.add(hendelse)
        personIder.add(personId)
    }
}