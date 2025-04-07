package no.nav.melosysskattehendelser.skatt

class PensjonsgivendeInntektConsumerFake : PensjonsgivendeInntektConsumer {

    override fun hentPensjonsgivendeInntekt(request: PensjonsgivendeInntektRequest) =
        PensjonsgivendeInntektResponse(
            norskPersonidentifikator = request.navPersonident,
            inntektsaar = request.inntektsaar,
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
}
