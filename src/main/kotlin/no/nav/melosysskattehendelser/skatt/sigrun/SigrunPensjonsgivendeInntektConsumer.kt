package no.nav.melosysskattehendelser.skatt.sigrun

import no.nav.melosysskattehendelser.metrics.Measured
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektConsumer
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektRequest
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

private val log = io.github.oshai.kotlinlogging.KotlinLogging.logger {}

/**
 * Sigrun fjernet GET-varianten av dette endepunktet fordi den krevde personidentifikator i
 * headeren Nav-Personident. Etter fjerningen svarte GET 403 insufficient_scope, ikke 404/405,
 * så feilen så ut som et tilgangsproblem. POST tar de samme parametrene i body - inkludert
 * rettighetspakke, som tidligere var en default-header på WebClienten.
 */
private const val RETTIGHETSPAKKE = "navtrygdeavgift"

private data class PensjonsgivendeInntektForFolketrygdenRequest(
    val personident: String,
    val inntektsaar: String,
    val rettighetspakke: String = RETTIGHETSPAKKE,
)

open class SigrunPensjonsgivendeInntektConsumer(private val webClient: WebClient) : PensjonsgivendeInntektConsumer {
    @Measured
    @Cacheable(value = ["pensjonsgivendeInntekt"], key = "#request.navPersonident + '-' + #request.inntektsaar")
    override fun hentPensjonsgivendeInntekt(request: PensjonsgivendeInntektRequest): PensjonsgivendeInntektResponse {
        return webClient.post()
            .uri("/api/v1/pensjonsgivendeinntektforfolketrygden")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                PensjonsgivendeInntektForFolketrygdenRequest(
                    personident = request.navPersonident,
                    inntektsaar = request.inntektsaar
                )
            )
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

                    // Alt som ikke er 2xx behandles som feil. Med status.isError ville en 3xx
                    // (f.eks. redirect til innlogging fra gatewayen) havnet i else-grenen og
                    // blitt rapportert som "Ingen body" - nettopp den intetsigende meldingen
                    // denne håndteringen finnes for å unngå.
                    !status.is2xxSuccessful -> response.createException().flatMap { exception ->
                        log.error {
                            "PensjonsgivendeInntekt api feilet - status: $status" +
                                ", inntektsaar: ${request.inntektsaar}" +
                                ", Nav-Call-Id: ${response.requestHeader("Nav-Call-Id")}" +
                                ", rettighetspakke: $RETTIGHETSPAKKE" +
                                ", Nav-Consumer-Id: ${response.requestHeader("Nav-Consumer-Id")}" +
                                ", responseHeaders: ${response.loggbareResponseHeadere()}" +
                                ", body: ${exception.responseBodyAsString.maskertOgAvkortet()}"
                        }
                        Mono.error(exception)
                    }

                    else -> response.bodyToMono(PensjonsgivendeInntektResponse::class.java)
                        // Et 2xx-svar med en body som ikke er en PensjonsgivendeInntektResponse
                        // deserialiseres stille til et objekt med bare nullfelt og tom inntektsliste.
                        // Det tolkes nedstrøms som en gyldig endring og publiseres. Samme feilklasse
                        // som feilsvarene over, bare på 2xx, så den må fanges her.
                        .flatMap { body ->
                            if (body.norskPersonidentifikator == null) Mono.error(
                                IllegalStateException(
                                    "Uventet body fra PensjonsgivendeInntekt api - " +
                                        "norskPersonidentifikator mangler, status: $status"
                                )
                            ) else Mono.just(body)
                        }
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

/**
 * Response-headere fra Sigrun er utenfor vår kontroll - vi vet ikke hva Sigrun eller proxyen
 * foran den sender tilbake (ekko av ident, Set-Cookie fra en gateway, ...). Derfor logges kun
 * en allow-liste, av samme grunn som request-headerne over hentes én og én.
 */
private val LOGGBARE_RESPONSE_HEADERE = listOf("Nav-Call-Id", "x-request-id", "Retry-After", "Content-Type")

private fun ClientResponse.loggbareResponseHeadere(): String =
    headers().asHttpHeaders()
        .let { headere -> LOGGBARE_RESPONSE_HEADERE.mapNotNull { navn -> headere.getFirst(navn)?.let { "$navn=$it" } } }
        .joinToString(prefix = "[", postfix = "]")

private const val MAKS_BODY_LENGDE = 500

/**
 * Sifferserier på 11-13 tegn - fødselsnummer, d-nummer og aktørid. Samme intensjon som
 * masken i logback.xml, men den er kun aktiv i prod-clusteret; her gjelder den i alle miljøer.
 */
private val PERSONIDENTIFIKATOR = Regex("""(^|\W)\d{11,13}(?=$|\W)""")

/**
 * Feilbodyen fra Sigrun er fritekst vi ikke eier og kan inneholde personidentifikatorer
 * (typisk "Ugyldig personidentifikator: <fnr>"), så den maskeres før logging. Den avkortes
 * også: svarer proxyen med en HTML-feilside blir bodyen titusenvis av tegn på én loggslinje.
 */
private fun String.maskertOgAvkortet(): String =
    PERSONIDENTIFIKATOR.replace(take(MAKS_BODY_LENGDE)) { "${it.groupValues[1]}***********" }
        .let { if (length > MAKS_BODY_LENGDE) "$it...[avkortet, $length tegn totalt]" else it }
