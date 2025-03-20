package no.nav.melosysskattehendelser.skatt

import java.util.concurrent.atomic.AtomicLong

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
        val metodeStats : Map<String, AtomicLong>,
    ) {
        fun applyReport(reportStats: (Stats) -> Unit) {
            reportStats(this)
        }
    }
}