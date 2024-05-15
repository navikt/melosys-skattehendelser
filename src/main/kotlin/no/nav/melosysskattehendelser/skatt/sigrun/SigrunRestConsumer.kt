package no.nav.melosysskattehendelser.skatt.sigrun

import no.nav.melosysskattehendelser.skatt.Hendelse
import no.nav.melosysskattehendelser.skatt.HendelseRequest
import no.nav.melosysskattehendelser.skatt.SkattHendelseConsumer
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

class SigrunRestConsumer(private val webClient: WebClient) : SkattHendelseConsumer {

    override fun hentHendelseListe(request: HendelseRequest): List<Hendelse> =
        webClient.get().uri("/api/skatteoppgjoer/hendelser")
            .header("x-sekvensnummer-fra", request.seksvensnummerFra.toString())
            .header("x-antall", request.antall.toString())
            .header("x-bruk-aktoerid", request.brukAktoerId.toString())
            .retrieve()
            .bodyToMono<List<Hendelse>>()
            .block() ?: emptyList()
}
