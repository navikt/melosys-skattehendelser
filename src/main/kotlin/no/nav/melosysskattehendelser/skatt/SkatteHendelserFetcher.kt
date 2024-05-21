package no.nav.melosysskattehendelser.skatt

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDate

private val log = KotlinLogging.logger { }

@Component
class SkatteHendelserFetcher(
    private val skatteHendelseConsumer: SkatteHendelseConsumer,
    @Value("\${skatt.fetcher.batch-size}") private val batchSize: Int
) {
    init {
        log.info("batchSize er satt til $batchSize")
    }

    fun hentHendelser(startSeksvensnummer: Long, batchDone: (seksvensnummer: Long) -> Unit) = sequence<Hendelse> {
        var seksvensnummerFra = startSeksvensnummer
        var hendelseListe: List<Hendelse>
        var totaltAntallHendelser = 0
        do {
            hendelseListe = hentSkatteHendelseser(seksvensnummerFra)
            if (hendelseListe.size > batchSize) error("hendelseListe.size ${hendelseListe.size} > batchSize $batchSize")
            val last = hendelseListe.lastOrNull() ?: break
            log.info(
                "Hentet ${hendelseListe.size} hendelser fra sekvensnummer $seksvensnummerFra til ${last.sekvensnummer} " +
                        "gjelderPeriode ${last.gjelderPeriode}"
            )
            seksvensnummerFra = last.sekvensnummer + 1
            yieldAll(hendelseListe)
            batchDone(seksvensnummerFra)
            totaltAntallHendelser += hendelseListe.size
            if (hendelseListe.size < batchSize) {
                break
            }
        } while (hendelseListe.size == batchSize)
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
            LocalDate.of(2022, 1, 1) // TODO: fin ut hva vi starter på, eller hvor vi henter dette fra
                .apply {
                    log.info("start dato for startSekvensnummer er nå hardkodet til $this")
                }
        )
}