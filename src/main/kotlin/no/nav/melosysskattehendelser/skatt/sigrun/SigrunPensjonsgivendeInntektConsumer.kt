package no.nav.melosysskattehendelser.skatt.sigrun

import no.nav.melosysskattehendelser.metrics.Measured
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektConsumer
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektRequest
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

private val log = mu.KotlinLogging.logger {}

open class SigrunPensjonsgivendeInntektConsumer(private val webClient: WebClient) : PensjonsgivendeInntektConsumer {
    @Measured
    @Cacheable(value = ["pensjonsgivendeInntekt"], key = "#request.navPersonident + '-' + #request.inntektsaar")
    override fun hentPensjonsgivendeInntekt(request: PensjonsgivendeInntektRequest): PensjonsgivendeInntektResponse {
        return webClient.get()
            .uri("/api/v1/pensjonsgivendeinntektforfolketrygden")
            .header("Nav-Personident", request.navPersonident)
            .header("inntektsaar", request.inntektsaar)
            .exchangeToMono { response ->
                if (response.statusCode() == HttpStatus.NOT_FOUND) {
                    log.warn { "PensjonsgivendeInntekt api status: 404 NOT_FOUND" }
                    Mono.just(
                        PensjonsgivendeInntektResponse(
                            slettet = true,
                            norskPersonidentifikator = request.navPersonident,
                            inntektsaar = request.inntektsaar,
                            pensjonsgivendeInntekt = emptyList()
                        )
                    )
                } else {
                    response.bodyToMono(PensjonsgivendeInntektResponse::class.java)
                }
            }
            .block() ?: throw IllegalStateException("Ingen body - kunne ikke hente PensjonsgivendeInntektResponse")
    }
}
