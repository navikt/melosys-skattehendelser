package no.nav.melosysskattehendelser.skatt.sigrun

import no.nav.melosysskattehendelser.metrics.Measured
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektConsumer
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektRequest
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

private val log = io.github.oshai.kotlinlogging.KotlinLogging.logger {}

open class SigrunPensjonsgivendeInntektConsumer(private val webClient: WebClient) : PensjonsgivendeInntektConsumer {
    @Measured
    @Cacheable(value = ["pensjonsgivendeInntekt"], key = "#request.navPersonident + '-' + #request.inntektsaar")
    override fun hentPensjonsgivendeInntekt(request: PensjonsgivendeInntektRequest): PensjonsgivendeInntektResponse {
        return webClient.get()
            .uri("/api/v1/pensjonsgivendeinntektforfolketrygden")
            .header("Nav-Personident", request.navPersonident)
            .header("inntektsaar", request.inntektsaar)
            .exchangeToMono { response ->
                val status = response.statusCode()
                when {
                    status == HttpStatus.NOT_FOUND -> {
                        log.warn { "PensjonsgivendeInntekt api status: 404 NOT_FOUND" }
                        Mono.just(
                            PensjonsgivendeInntektResponse(
                                slettet = true,
                                norskPersonidentifikator = request.navPersonident,
                                inntektsaar = request.inntektsaar,
                                pensjonsgivendeInntekt = emptyList()
                            )
                        )
                    }

                    status.isError -> response.createException().flatMap { exception ->
                        log.error {
                            "PensjonsgivendeInntekt api feilet - status: $status" +
                                ", inntektsaar: ${request.inntektsaar}" +
                                ", Nav-Call-Id: ${response.requestHeader("Nav-Call-Id")}" +
                                ", rettighetspakke: ${response.requestHeader("rettighetspakke")}" +
                                ", Nav-Consumer-Id: ${response.requestHeader("Nav-Consumer-Id")}" +
                                ", responseHeaders: ${response.headers().asHttpHeaders()}" +
                                ", body: ${exception.responseBodyAsString}"
                        }
                        Mono.error(exception)
                    }

                    else -> response.bodyToMono(PensjonsgivendeInntektResponse::class.java)
                        .switchIfEmpty(
                            Mono.error(
                                IllegalStateException(
                                    "Ingen body - kunne ikke hente PensjonsgivendeInntektResponse, status: $status"
                                )
                            )
                        )
                }
            }
            .block() ?: throw IllegalStateException("Ingen body - kunne ikke hente PensjonsgivendeInntektResponse")
    }
}

/**
 * Leser en header fra det utgående kallet. Kun headere uten personopplysninger skal logges,
 * derfor hentes de eksplisitt én og én i stedet for å dumpe alle (Authorization og
 * Nav-Personident må aldri havne i loggen).
 */
private fun ClientResponse.requestHeader(name: String): String? =
    request().headers.getFirst(name)
