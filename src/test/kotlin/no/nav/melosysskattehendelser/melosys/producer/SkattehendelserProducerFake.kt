package no.nav.melosysskattehendelser.melosys.producer

import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse

class SkattehendelserProducerFake : SkattehendelserProducer {
    val hendelser = mutableListOf<MelosysSkatteHendelse>()
    val personIder = mutableListOf<Long?>()
    var feilVedNesteSending: Exception? = null
    override var dryRun: Boolean = false

    fun reset() = apply {
        hendelser.clear()
        personIder.clear()
        feilVedNesteSending = null
        dryRun = false
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