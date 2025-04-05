package no.nav.melosysskattehendelser.skatt.sigrun

import no.nav.melosysskattehendelser.metrics.Measured
import no.nav.melosysskattehendelser.skatt.Hendelse
import no.nav.melosysskattehendelser.skatt.HendelseRequest
import no.nav.melosysskattehendelser.skatt.SkatteHendelseConsumer
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.LocalDate

open class SigrunRestConsumer(
    private val webClient: WebClient,
) : SkatteHendelseConsumer {

    @Measured
    override fun hentHendelseListe(request: HendelseRequest): List<Hendelse> =
        webClient.get()
            .uri { uriBuilder ->
                uriBuilder.path("/api/v1/pensjonsgivendeinntektforfolketrygden/hendelser")
                    .queryParam("fraSekvensnummer", request.seksvensnummerFra)
                    .queryParam("antall", request.antall)
                    .build()
            }
            .header("bruk-aktoerid", request.brukAktoerId.toString())
            .retrieve()
            .bodyToMono<Map<String, List<Hendelse>>>()
            .block()?.let { it["hendelser"] ?: throw IllegalStateException("Response body inneholder ikke 'hendelser', var: $it") }
            ?: throw IllegalStateException("Ingen body - kunne ikke hente hendelser")

    override fun getConsumerId(): String = "sigrun-skatteoppgjoer-hendelser"

    override fun getStartSekvensnummer(start: LocalDate): Long = hentStartSekvensnummer(start).sekvensnummer

    private fun hentStartSekvensnummer(start: LocalDate): StartSekvensnummer =
        webClient.get()
            .uri { uriBuilder ->
                uriBuilder.path("/api/v1/pensjonsgivendeinntektforfolketrygden/hendelser/start")
                    .queryParam("dato", start.toString())
                    .build()
            }
            .retrieve()
            .bodyToMono<StartSekvensnummer>()
            .block() ?: throw IllegalStateException("Ingen body - kunne ikke hente start sekvensnummer")

    private data class StartSekvensnummer(val sekvensnummer: Long)
}
