package no.nav.melosysskattehendelser.skatt.sigrun

import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektConsumer
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektRequest
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

open class SigrunPensjonsgivendeInntektConsumer(private val webClient: WebClient) : PensjonsgivendeInntektConsumer {
    @Cacheable(value = ["pensjonsgivendeInntekt"], key = "#request.navPersonident + '-' + #request.inntektsaar")
    override fun hentPensjonsgivendeInntekt(request: PensjonsgivendeInntektRequest): PensjonsgivendeInntektResponse {
        return webClient.get()
            .uri("/api/v1/pensjonsgivendeinntektforfolketrygden")
            .header("Nav-Personident", request.navPersonident)
            .header("inntektsaar", request.inntektsaar)
            .retrieve()
            .bodyToMono<PensjonsgivendeInntektResponse>()
            .block() ?: throw IllegalStateException("Ingen body - kunne ikke hente PensjonsgivendeInntektResponse")
    }
}
