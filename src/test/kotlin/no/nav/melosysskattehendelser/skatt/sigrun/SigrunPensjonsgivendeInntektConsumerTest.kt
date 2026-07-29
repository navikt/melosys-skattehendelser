package no.nav.melosysskattehendelser.skatt.sigrun

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

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
            createGetRequest("26468141638")
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
            createGetRequest("26468141637")
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(404)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                )
        )

        val inntekt = sigrunPensjonsgivendeInntektConsumer.hentPensjonsgivendeInntekt(
            PensjonsgivendeInntektRequest(
                inntektsaar = "2024",
                navPersonident = "26468141637"
            )
        )

        inntekt.slettet shouldBe true

    }

    @Test
    fun `skal kaste feil med statuskode nar 2xx svar mangler body`() {
        wireMockServer.stubFor(
            createGetRequest("26468141639")
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(204)
                )
        )

        shouldThrow<IllegalStateException> {
            sigrunPensjonsgivendeInntektConsumer.hentPensjonsgivendeInntekt(
                PensjonsgivendeInntektRequest(inntektsaar = "2024", navPersonident = "26468141639")
            )
        }.message shouldContain "204"
    }

    @Test
    fun `skal kaste feil med statuskode nar api svarer 500 uten body`() {
        wireMockServer.stubFor(
            createGetRequest("26468141640")
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(500)
                )
        )

        shouldThrow<WebClientResponseException> {
            sigrunPensjonsgivendeInntektConsumer.hentPensjonsgivendeInntekt(
                PensjonsgivendeInntektRequest(inntektsaar = "2024", navPersonident = "26468141640")
            )
        }.statusCode.value() shouldBe 500
    }

    @Test
    fun `skal ikke tolke feilmelding-body fra 500 som tom inntekt`() {
        wireMockServer.stubFor(
            createGetRequest("26468141641")
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(500)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""{"melding":"Intern feil i Sigrun"}""")
                )
        )

        shouldThrow<WebClientResponseException> {
            sigrunPensjonsgivendeInntektConsumer.hentPensjonsgivendeInntekt(
                PensjonsgivendeInntektRequest(inntektsaar = "2024", navPersonident = "26468141641")
            )
        }.statusCode.value() shouldBe 500
    }

    @Test
    fun `skal logge statuskode, body og Nav-Call-Id ved 403 uten å lekke personident`() {
        wireMockServer.stubFor(
            createGetRequest("26468141642")
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(403)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""{"melding":"Mangler tilgang til rettighetspakke navtrygdeavgift"}""")
                )
        )
        val consumerMedCallId = SigrunPensjonsgivendeInntektConsumer(
            WebClient.builder()
                .baseUrl("http://localhost:${wireMockServer.port()}")
                .filter(ExchangeFilterFunction.ofRequestProcessor { request ->
                    Mono.just(ClientRequest.from(request).header("Nav-Call-Id", "CallId_test_123").build())
                })
                .build()
        )

        val logg = ListAppender<ILoggingEvent>().apply { start() }
        val logger = LoggerFactory.getLogger("no.nav.melosysskattehendelser.skatt.sigrun") as Logger
        logger.addAppender(logg)

        try {
            shouldThrow<WebClientResponseException> {
                consumerMedCallId.hentPensjonsgivendeInntekt(
                    PensjonsgivendeInntektRequest(inntektsaar = "2024", navPersonident = "26468141642")
                )
            }.statusCode.value() shouldBe 403
        } finally {
            logger.detachAppender(logg)
        }

        val melding = logg.list.single { it.level == Level.ERROR }.formattedMessage
        melding shouldContain "403"
        melding shouldContain "Mangler tilgang til rettighetspakke navtrygdeavgift"
        melding shouldContain "CallId_test_123"
        melding shouldContain "inntektsaar: 2024"
        melding shouldNotContain "26468141642"
    }

    private fun createGetRequest(navPersonident: String): MappingBuilder =
        WireMock.get(WireMock.urlPathEqualTo("/api/v1/pensjonsgivendeinntektforfolketrygden"))
            .withHeader("Nav-Personident", WireMock.equalTo(navPersonident))
}