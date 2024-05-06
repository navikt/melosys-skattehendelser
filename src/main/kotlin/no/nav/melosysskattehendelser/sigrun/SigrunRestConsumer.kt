package no.nav.melosysskattehendelser.sigrun

import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

class SigrunRestConsumer(private val webClient: WebClient) {

    fun hentHendelseListe(request: HendelseRequest): List<Hendelse> =
        webClient.get().uri("/api/skatteoppgjoer/hendelser")
            .accept(MediaType.APPLICATION_JSON)
            .header("x-sekvensnummer-fra", request.seksvensnummerFra.toString())
            .header("x-antall", request.antall.toString())
            .header("x-bruk-aktoerid", request.brukAktoerId.toString())
            .header("Nav-Call-Id", request.navCallId)
            .header("Nav-Consumer-Id", request.consumerId)
            .retrieve()
            .bodyToMono<List<Hendelse>>()
            .block() ?: emptyList()
}

data class HendelseRequest(
    val seksvensnummerFra: Int,
    val antall: Int,
    val brukAktoerId: Boolean,
    val navCallId: String,
    val consumerId: String,
)

data class Hendelse(
    val gjelderPeriode: String,
    val identifikator: String,
    val sekvensnummer: Int,
    val somAktoerid: Boolean
)