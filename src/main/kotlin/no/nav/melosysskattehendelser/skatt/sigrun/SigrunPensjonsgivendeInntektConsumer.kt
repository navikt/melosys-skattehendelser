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
import reactor.core.scheduler.Schedulers
import java.time.Duration

private val log = io.github.oshai.kotlinlogging.KotlinLogging.logger {}

private const val RETTIGHETSPAKKE = "navtrygdeavgift"

/**
 * Feltnavnene er Sigrun sine (personident, ikke navPersonident). Rettighetspakke leses
 * kun fra bodyen - den har ingen effekt som header.
 */
internal data class PensjonsgivendeInntektForFolketrygdenRequest(
    val personident: String,
    val inntektsaar: String,
    val rettighetspakke: String = RETTIGHETSPAKKE,
) {
    // Uten ident: WebClient skriver toString() i DEBUG-loggen. Internal for testen.
    override fun toString(): String =
        "PensjonsgivendeInntektForFolketrygdenRequest(personident=***********, inntektsaar=$inntektsaar, rettighetspakke=$rettighetspakke)"
}

const val PENSJONSGIVENDE_INNTEKT_CACHE = "pensjonsgivendeInntekt"

open class SigrunPensjonsgivendeInntektConsumer(private val webClient: WebClient) : PensjonsgivendeInntektConsumer {
    @Measured
    @Cacheable(
        value = [PENSJONSGIVENDE_INNTEKT_CACHE],
        key = "T(no.nav.melosysskattehendelser.skatt.sigrun.SigrunPensjonsgivendeInntektConsumer).cachenokkel(#request)"
    )
    override fun hentPensjonsgivendeInntekt(request: PensjonsgivendeInntektRequest): PensjonsgivendeInntektResponse =
        hent(request, tidsfrist = null)

    /** Skriver ikke til cachen. Kaster IllegalStateException når Sigrun ikke har svart innen [tidsfrist]. */
    @Measured
    open fun hentUtenCache(request: PensjonsgivendeInntektRequest, tidsfrist: Duration): PensjonsgivendeInntektResponse =
        hent(request, tidsfrist)

    private fun hent(request: PensjonsgivendeInntektRequest, tidsfrist: Duration?): PensjonsgivendeInntektResponse {
        val svar = webClient.post()
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
        if (tidsfrist == null) {
            return svar.block() ?: throw IllegalStateException("Ingen body - kunne ikke hente PensjonsgivendeInntektResponse")
        }
        // Filtrene kjører når kallet startes, og AzureContextExchangeFilter henter token synkront der.
        // På en egen tråd teller også det med i tidsfristen; block(tidsfrist) avbryter kallet når den går ut.
        return svar.subscribeOn(Schedulers.boundedElastic()).block(tidsfrist)
            ?: throw IllegalStateException("Ingen body - kunne ikke hente PensjonsgivendeInntektResponse")
    }

    companion object {
        /** Nøkkelen i [PENSJONSGIVENDE_INNTEKT_CACHE], både for @Cacheable over og for den som tømmer cachen. */
        @JvmStatic
        fun cachenokkel(request: PensjonsgivendeInntektRequest) = "${request.navPersonident}-${request.inntektsaar}"
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
 * Sifferserier på 11-13 tegn - fødselsnummer, d-nummer og aktørid - også når de står inntil en
 * bokstav eller understrek (`personident_<fnr>`). Samme intensjon som masken i logback.xml, men
 * den er kun aktiv i prod-clusteret; her gjelder den i alle miljøer.
 */
private val PERSONIDENTIFIKATOR = Regex("""(?<!\d)\d{11,13}(?!\d)""")

/**
 * Feilbodyen fra Sigrun er fritekst vi ikke eier og kan inneholde personidentifikatorer
 * (typisk "Ugyldig personidentifikator: <fnr>"), så den maskeres før logging. Den avkortes
 * også: svarer proxyen med en HTML-feilside blir bodyen titusenvis av tegn på én loggslinje.
 */
internal fun String.maskertOgAvkortet(): String =
    PERSONIDENTIFIKATOR.replace(take(MAKS_BODY_LENGDE), "***********")
        .let { if (length > MAKS_BODY_LENGDE) "$it...[avkortet, $length tegn totalt]" else it }
