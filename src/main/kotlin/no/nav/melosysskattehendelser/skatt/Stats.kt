package no.nav.melosysskattehendelser.skatt

data class Stats(
    val totaltAntallHendelser: Int,
    val antallBatcher: Int,
    val sisteBatchSize: Int
) {
    fun applyReport(reportStats: (Stats) -> Unit) {
        reportStats(this)
    }
}