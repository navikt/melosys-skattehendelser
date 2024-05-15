package no.nav.melosysskattehendelser.skatt

import mu.KotlinLogging
import org.springframework.stereotype.Component

@Component
class SkattHendelserFetcher(private val skattHendelseConsumer: SkattHendelseConsumer) {
    private val log = KotlinLogging.logger { }

    fun hentHendelser(startSeksvensnummer: Int) = sequence<Hendelse> {
        var seksvensnummerFra = startSeksvensnummer
        var hentHendelseListe: List<Hendelse>
        val antall = 500
        do {
            hentHendelseListe = skattHendelseConsumer.hentHendelseListe(
                HendelseRequest(
                    seksvensnummerFra = seksvensnummerFra,
                    antall = antall,
                    brukAktoerId = false
                )
            )
            val last = hentHendelseListe.last()
            seksvensnummerFra = last.sekvensnummer + 1
            log.info(
                "Hentet ${hentHendelseListe.size} hendelser fra sekvensnummer $seksvensnummerFra til ${last.sekvensnummer} " +
                        "gjelderPeriode ${last.gjelderPeriode}"
            )
            if (hentHendelseListe.size < antall) {
                log.info("Siste hendelse funnet: ${hentHendelseListe.last()}")
            }
            yieldAll(hentHendelseListe)
        } while (hentHendelseListe.size >= antall)
    }
}