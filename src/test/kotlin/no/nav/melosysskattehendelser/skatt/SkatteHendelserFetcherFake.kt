package no.nav.melosysskattehendelser.skatt

import java.time.LocalDate

class SkatteHendelserFetcherFake : SkatteHendelserFetcher {
    val hendelser = mutableListOf<Hendelse>()
    var batchSize = 500
    var sekvensnummerForDato = 1L
    var høyesteUtgitt: Long? = null
        private set
    var vedUtgivelse: (Hendelse) -> Unit = {}

    fun reset() = apply {
        hendelser.clear()
        batchSize = 500
        sekvensnummerForDato = 1L
        høyesteUtgitt = null
        vedUtgivelse = {}
    }

    override fun hentHendelser(
        startSeksvensnummer: Long,
        batchDone: (seksvensnummer: Long) -> Unit,
        reportStats: (stats: SkatteHendelserFetcher.Stats) -> Unit
    ): Sequence<Hendelse> = sequence {
        hendelser.filter { it.sekvensnummer >= startSeksvensnummer }.chunked(batchSize).forEach { batch ->
            batch.forEach {
                høyesteUtgitt = it.sekvensnummer
                vedUtgivelse(it)
                yield(it)
            }
            batchDone(batch.last().sekvensnummer + 1)
        }
    }

    override val consumerId: String
        get() = "fake"

    override val omlesingConsumerId: String
        get() = "fake-omlesing"

    override val startSekvensnummer: Long
        get() = 1L

    override fun startSekvensnummerFra(dato: LocalDate): Long = sekvensnummerForDato
}
