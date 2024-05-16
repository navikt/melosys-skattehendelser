package no.nav.melosysskattehendelser.skatt

import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class SkattHendelserFetcher(private val skattHendelseConsumer: SkattHendelseConsumer) {
    private val log = KotlinLogging.logger { }

    fun hentHendelser(startSeksvensnummer: Long, batchDone: (seksvensnummer: Long) -> Unit = {}) = sequence<Hendelse> {
        var seksvensnummerFra = startSeksvensnummer
        var hendelseListe: List<Hendelse>
        var totaltAntallHendelser = 0
        do {
            hendelseListe = hentSkatteHendelseser(seksvensnummerFra)
            if (hendelseListe.size > BATCH_SIZE) error("hendelseListe.size${hendelseListe.size} > batchSize$BATCH_SIZE")
            val last = hendelseListe.lastOrNull() ?: break
            log.info(
                "Hentet ${hendelseListe.size} hendelser fra sekvensnummer $seksvensnummerFra til ${last.sekvensnummer} " +
                        "gjelderPeriode ${last.gjelderPeriode}"
            )
            seksvensnummerFra = last.sekvensnummer + 1
            yieldAll(hendelseListe)
            batchDone(seksvensnummerFra)
            totaltAntallHendelser += hendelseListe.size
            if (hendelseListe.size < BATCH_SIZE) {
                break
            }
        } while (hendelseListe.size == BATCH_SIZE)
        log.info("totalt antall hendelser prossessert: $totaltAntallHendelser seksvensnummerFra er nå: $seksvensnummerFra")
    }

    private fun hentSkatteHendelseser(seksvensnummerFra: Long) = skattHendelseConsumer.hentHendelseListe(
        HendelseRequest(
            seksvensnummerFra = seksvensnummerFra,
            antall = BATCH_SIZE,
            brukAktoerId = false
        )
    )

    val consumerId
        get() = skattHendelseConsumer.getConsumerId()

    val startSekvensnummer
        get() = skattHendelseConsumer.getStartSekvensnummer(
            LocalDate.of(2022, 1, 1) // TODO: fin ut hva vi starter på, eller hvor vi henter dette fra
                .apply {
                    log.info("start dato for startSekvensnummer er nå hardkodet til $this")
                }
        )

    companion object {
        const val BATCH_SIZE = 500
    }
}