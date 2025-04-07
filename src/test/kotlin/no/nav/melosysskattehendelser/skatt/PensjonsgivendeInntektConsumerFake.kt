package no.nav.melosysskattehendelser.skatt

class PensjonsgivendeInntektConsumerFake : PensjonsgivendeInntektConsumer {
    private val pensjonsgivendeInntekt = mutableMapOf<Int, PensjonsgivendeInntektResponse>()
    private var addIndex = 0
    private var fetchIndex = 0

    fun leggTilPensjonsgivendeInntekt(
        ident: String = "123",
        inntektsaar: String = "2023",
        response: (PensjonsgivendeInntektResponse) -> PensjonsgivendeInntektResponse = { it }
    ) = apply {
        val original = default(ident, inntektsaar)
        pensjonsgivendeInntekt[addIndex++] = response(original)
    }

    fun reset() = apply {
        addIndex = 0
        fetchIndex = 0
        pensjonsgivendeInntekt.clear()
    }

    fun default(ident: String, inntektsaar: String) = PensjonsgivendeInntektResponse(
        norskPersonidentifikator = ident,
        inntektsaar = inntektsaar,
        pensjonsgivendeInntekt = listOf(
            PensjonsgivendeInntekt(
                skatteordning = "SKATTEORDNING",
                datoForFastsetting = "2023-01-01",
                pensjonsgivendeInntektAvLoennsinntekt = "1000",
                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = "2000",
                pensjonsgivendeInntektAvNaeringsinntekt = "3000",
                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = "4000"
            )
        )
    )

    override fun hentPensjonsgivendeInntekt(request: PensjonsgivendeInntektRequest): PensjonsgivendeInntektResponse {
        return pensjonsgivendeInntekt[fetchIndex++]
            ?: throw IllegalStateException("Fant ikke pensjonsgivendeInntektRequest ${fetchIndex - 1}")
    }
}
