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
    val slettet: Boolean = false,
    val norskPersonidentifikator: String,
    val inntektsaar: String,
    val pensjonsgivendeInntekt: List<PensjonsgivendeInntekt>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PensjonsgivendeInntekt(
    val skatteordning: String,
    val datoForFastsetting: String,
    val pensjonsgivendeInntektAvLoennsinntekt: String?,
    val pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel: String?,
    val pensjonsgivendeInntektAvNaeringsinntekt: String?,
    val pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage: String?
)
