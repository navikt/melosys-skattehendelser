package no.nav.melosysskattehendelser.skatt

import java.time.LocalDate

interface SkatteHendelserFetcher {
    fun hentHendelser(
        startSeksvensnummer: Long,
        batchDone: (seksvensnummer: Long) -> Unit,
        reportStats: (stats: Stats) -> Unit = {}
    ) : Sequence<Hendelse>

    val consumerId: String

    val omlesingConsumerId: String

    val startSekvensnummer : Long

    fun startSekvensnummerFra(dato: LocalDate): Long

    data class Stats(
        val totaltAntallHendelser: Int,
        val antallBatcher: Int,
        val sisteBatchSize: Int,
    ) {
        fun applyReport(reportStats: (Stats) -> Unit) {
            reportStats(this)
        }
    }
}