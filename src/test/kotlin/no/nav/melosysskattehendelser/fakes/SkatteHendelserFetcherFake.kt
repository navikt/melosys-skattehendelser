package no.nav.melosysskattehendelser.fakes

import no.nav.melosysskattehendelser.skatt.Hendelse
import no.nav.melosysskattehendelser.skatt.SkatteHendelserFetcher

class SkatteHendelserFetcherFake : SkatteHendelserFetcher {
    val hendelser = mutableListOf<Hendelse>()

    fun reset() = apply {
        hendelser.clear()
    }

    override fun hentHendelser(
        startSeksvensnummer: Long,
        batchDone: (seksvensnummer: Long) -> Unit,
        reportStats: (stats: SkatteHendelserFetcher.Stats) -> Unit
    ): Sequence<Hendelse> {
        return hendelser.asSequence()
    }

    override val consumerId: String
        get() = "fake"

    override val startSekvensnummer: Long
        get() = 1L
}