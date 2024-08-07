package no.nav.melosysskattehendelser.skatt

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDate

private val log = KotlinLogging.logger { }

@Component
class SkatteHendelserFetcher(
    private val skatteHendelseConsumer: SkatteHendelseConsumer,
    @Value("\${skatt.fetcher.batch-size}") private val batchSize: Int,
    @Value("\${skatt.fetcher.start-dato}") private val startDato: LocalDate
) {
    init {
        log.info("batchSize er satt til $batchSize")
    }


    fun hentHendelser(
        startSeksvensnummer: Long,
        batchDone: (seksvensnummer: Long) -> Unit,
        reportStats: (stats: Stats) -> Unit = {}
    ) = sequence<Hendelse> {
        var seksvensnummerFra = startSeksvensnummer
        var hendelseListe: List<Hendelse>
        var totaltAntallHendelser = 0
        var antallBatcher = 0
        while (true) {
            hendelseListe = hentSkatteHendelseser(seksvensnummerFra)
            if (hendelseListe.size > batchSize) error("hendelseListe.size ${hendelseListe.size} > batchSize $batchSize")
            val last = hendelseListe.lastOrNull() ?: break
            log.info(
                "Hentet ${hendelseListe.size} hendelser fra sekvensnummer $seksvensnummerFra til ${last.sekvensnummer}"
            )
            seksvensnummerFra = last.sekvensnummer + 1
            yieldAll(hendelseListe)
            batchDone(seksvensnummerFra)
            totaltAntallHendelser += hendelseListe.size
            Stats(
                totaltAntallHendelser = totaltAntallHendelser,
                antallBatcher = ++antallBatcher,
                sisteBatchSize = hendelseListe.size
            ).applyReport(reportStats)
        }
        log.info("totalt antall hendelser prossessert: $totaltAntallHendelser seksvensnummerFra er nå: $seksvensnummerFra")
    }

    private fun hentSkatteHendelseser(seksvensnummerFra: Long) = skatteHendelseConsumer.hentHendelseListe(
        HendelseRequest(
            seksvensnummerFra = seksvensnummerFra,
            antall = batchSize,
            brukAktoerId = false
        )
    )

    val consumerId
        get() = skatteHendelseConsumer.getConsumerId()

    val startSekvensnummer
        get() = skatteHendelseConsumer.getStartSekvensnummer(
            startDato
                .apply {
                    log.info("start dato for startSekvensnummer: $this")
                }
        )

    data class Stats(
        val totaltAntallHendelser: Int,
        val antallBatcher: Int,
        val sisteBatchSize: Int
    ) {
        fun applyReport(reportStats: (Stats) -> Unit) {
            reportStats(this)
        }
    }
}