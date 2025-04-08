package no.nav.melosysskattehendelser.skatt.sigrun

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SigrunPensjonsgivendeInntektConsumerTest {

    private val wireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort()).apply {
        start()
    }
    private val sigrunPensjonsgivendeInntektConsumer = SigrunPensjonsgivendeInntektConsumer(
        WebClient.builder()
            .baseUrl("http://localhost:${wireMockServer.port()}")
            .build()
    )

    @AfterAll
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun `skal hente inntekt`() {
        wireMockServer.stubFor(
            createGetRequest()
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(
                            """
                                {
                                    "norskPersonidentifikator": "26468141638",
                                    "inntektsaar": "2024",
                                    "pensjonsgivendeInntekt": [
                                        {
                                            "skatteordning": "FASTLAND",
                                            "datoForFastsetting": "2020-08-01",
                                            "pensjonsgivendeInntektAvLoennsinntekt": "200000",
                                            "pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel": null,
                                            "pensjonsgivendeInntektAvNaeringsinntekt": null,
                                            "pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage": null
                                        }
                                    ]
                                }                                
                            """.trimIndent()
                        )
                )
        )

        val inntekt = sigrunPensjonsgivendeInntektConsumer.hentPensjonsgivendeInntekt(
            PensjonsgivendeInntektRequest(
                inntektsaar = "2024",
                navPersonident = "26468141638"
            )
        )

        inntekt.run {
            slettet shouldBe false
            norskPersonidentifikator shouldBe "26468141638"
            inntektsaar shouldBe "2024"
            pensjonsgivendeInntekt.shouldHaveSize(1)
        }

    }

    @Test
    fun `skal håntere 404`() {
        wireMockServer.stubFor(
            createGetRequest()
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(404)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                )
        )

        val inntekt = sigrunPensjonsgivendeInntektConsumer.hentPensjonsgivendeInntekt(
            PensjonsgivendeInntektRequest(
                inntektsaar = "2024",
                navPersonident = "26468141638"
            )
        )

        inntekt.slettet shouldBe true

    }

    private fun createGetRequest(): MappingBuilder =
        WireMock.get(WireMock.urlPathEqualTo("/api/v1/pensjonsgivendeinntektforfolketrygden"))
}
