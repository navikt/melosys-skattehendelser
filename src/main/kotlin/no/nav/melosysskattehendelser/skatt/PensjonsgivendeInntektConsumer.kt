package no.nav.melosysskattehendelser.skatt

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


fun interface PensjonsgivendeInntektConsumer {
    fun hentPensjonsgivendeInntekt(request: PensjonsgivendeInntektRequest): PensjonsgivendeInntektResponse
}

data class PensjonsgivendeInntektRequest(
    val inntektsaar: String,
    val navPersonident: String,
)

data class PensjonsgivendeInntektResponse(
    val norskPersonidentifikator: String,
    val inntektsaar: String,
    val pensjonsgivendeInntekt: List<PensjonsgivendeInntekt>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PensjonsgivendeInntekt(
    val datoForFastsetting: String
)

