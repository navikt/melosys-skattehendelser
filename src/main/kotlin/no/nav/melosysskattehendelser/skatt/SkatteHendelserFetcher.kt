package no.nav.melosysskattehendelser.skatt

interface SkatteHendelserFetcher {
    fun hentHendelser(
        startSeksvensnummer: Long,
        batchDone: (seksvensnummer: Long) -> Unit,
        reportStats: (stats: Stats) -> Unit = {}
    ) : Sequence<Hendelse>

    val consumerId: String

    val startSekvensnummer : Long

    data class Stats(
        val totaltAntallHendelser: Int,
        val antallBatcher: Int,
        val sisteBatchSize: Int,
        val nanoSecUsed: Long = 0
    ) {
        fun applyReport(reportStats: (Stats) -> Unit) {
            reportStats(this)
        }
    }
}