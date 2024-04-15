package no.nav.melosysskattehendelser.sigrun

import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

class SigrunRestConsumer(private val webClient: WebClient) {

    fun hentHendelseListe(): List<Hendelse>? {
        return webClient.get().uri("/api/skatteoppgjoer/hendelser")
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono<List<Hendelse>>()
            .block()
    }
}

data class Hendelse(
    val gjeldendePeriode: String,
    val identifikator: String,
    val sekvensnummer: Int,
    val somAktoerid: Boolean
)