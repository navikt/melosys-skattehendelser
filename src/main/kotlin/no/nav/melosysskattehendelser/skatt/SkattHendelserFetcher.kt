package no.nav.melosysskattehendelser.skatt

import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class SkattHendelserFetcher(private val skattHendelseConsumer: SkattHendelseConsumer) {
    private val log = KotlinLogging.logger { }

    fun hentHendelser(startSeksvensnummer: Long) = sequence<Hendelse> {
        var seksvensnummerFra = startSeksvensnummer
        var hentHendelseListe: List<Hendelse>
        val batchSize = 500
        var totaltAntallHendelser = 0
        do {
            hentHendelseListe = skattHendelseConsumer.hentHendelseListe(
                HendelseRequest(
                    seksvensnummerFra = seksvensnummerFra,
                    antall = batchSize,
                    brukAktoerId = false
                )
            )
            if (hentHendelseListe.size > batchSize) error("hentHendelseListe.size${hentHendelseListe.size} > batchSize$batchSize")
            val last = hentHendelseListe.lastOrNull() ?: break
            log.info(
                "Hentet ${hentHendelseListe.size} hendelser fra sekvensnummer $seksvensnummerFra til ${last.sekvensnummer} " +
                        "gjelderPeriode ${last.gjelderPeriode}"
            )
            seksvensnummerFra = last.sekvensnummer + 1
            yieldAll(hentHendelseListe)
            totaltAntallHendelser += hentHendelseListe.size
            if (hentHendelseListe.size < batchSize) {
                break
            }
        } while (hentHendelseListe.size == batchSize)
        log.info("totalt antall hendelser prossessert: $totaltAntallHendelser seksvensnummerFra er nå: $seksvensnummerFra")
    }

    val consumerId
        get() = skattHendelseConsumer.getConsumerId()

    val startSekvensnummer
        get() = skattHendelseConsumer.getStartSekvensnummer(
            LocalDate.of(2022, 1, 1) // TODO: fin ut hva vi starter på, eller hvor vi henter dette fra
                .apply {
                    log.info("start dato for startSekvensnummer er nå hardkodet til $this")
                }
        )
}