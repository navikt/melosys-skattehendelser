package no.nav.melosysskattehendelser.skatt

import mu.KotlinLogging
import no.nav.melosysskattehendelser.skatt.SkatteHendelserFetcher.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDate

private val log = KotlinLogging.logger { }

@Component
class SkatteHendelserFetcherAPI(
    private val skatteHendelseConsumer: SkatteHendelseConsumer,
    @Value("\${skatt.fetcher.batch-size}") private val batchSize: Int,
    @Value("\${skatt.fetcher.start-dato}") private val startDato: LocalDate
) : SkatteHendelserFetcher {
    init {
        log.info("batchSize er satt til $batchSize")
    }


    override fun hentHendelser(
        startSeksvensnummer: Long,
        batchDone: (seksvensnummer: Long) -> Unit,
        reportStats: (stats: Stats) -> Unit
    ): Sequence<Hendelse> = sequence<Hendelse> {
        var seksvensnummerFra = startSeksvensnummer
        var hendelseListe: List<Hendelse>
        var totaltAntallHendelser = 0
        var antallBatcher = 0
        while (true) {
            hendelseListe = hentSkatteHendelser(seksvensnummerFra)
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

    private fun hentSkatteHendelser(seksvensnummerFra: Long) = skatteHendelseConsumer.hentHendelseListe(
        HendelseRequest(
            seksvensnummerFra = seksvensnummerFra,
            antall = batchSize,
            brukAktoerId = false
        )
    )

    override val consumerId
        get() = skatteHendelseConsumer.getConsumerId()

    override val startSekvensnummer
        get() = skatteHendelseConsumer.getStartSekvensnummer(
            startDato
                .apply {
                    log.info("start dato for startSekvensnummer: $this")
                }
        )
}