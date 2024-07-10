package no.nav.melosysskattehendelser.skatt.sigrun

import mu.KotlinLogging
import no.nav.melosysskattehendelser.skatt.Hendelse
import no.nav.melosysskattehendelser.skatt.HendelseRequest
import no.nav.melosysskattehendelser.skatt.SkatteHendelseConsumer
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.LocalDate

class SigrunRestConsumer(private val webClient: WebClient) : SkatteHendelseConsumer {
    private val log = KotlinLogging.logger { }

    override fun hentHendelseListe(request: HendelseRequest): List<Hendelse> =
        webClient.get().uri("/api/skatteoppgjoer/hendelser")
            .header("x-sekvensnummer-fra", request.seksvensnummerFra.toString())
            .header("x-antall", request.antall.toString())
            .header("x-bruk-aktoerid", request.brukAktoerId.toString())
            .retrieve()
            .bodyToMono<List<Hendelse>>()
            .block() ?: emptyList()

    override fun getConsumerId(): String = "sigrun-skatteoppgjoer-hendelser"

    override fun getStartSekvensnummer(start: LocalDate): Long {
        return hentStartSekvensnummer(start).sekvensnummer
    }

    private fun hentStartSekvensnummer(start: LocalDate): StartSekvensnummer =
        webClient.get()
            .uri { uriBuilder ->
                uriBuilder.path("/api/skatteoppgjoer/hendelser/start")
                    .queryParam("dato", start.toString())
                    .build()
            }
            .retrieve()
            .bodyToMono<StartSekvensnummer>()
            .block() ?: throw IllegalStateException("Ingen body - kunne ikke hente start sekvensnummer")

    private data class StartSekvensnummer(val sekvensnummer: Long)
}
