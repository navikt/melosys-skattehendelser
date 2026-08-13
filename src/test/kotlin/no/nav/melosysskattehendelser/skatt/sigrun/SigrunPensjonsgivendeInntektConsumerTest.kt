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
import io.kotest.matchers.ints.shouldBeLessThan
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
            createPostRequest("26468141638")
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
    fun `skal sende personident, inntektsaar og rettighetspakke i body - ikke i header`() {
        val navPersonident = "26468141647"
        wireMockServer.stubFor(
            createPostRequest(navPersonident)
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(
                            """
                                {
                                    "norskPersonidentifikator": "$navPersonident",
                                    "inntektsaar": "2024",
                                    "pensjonsgivendeInntekt": []
                                }
                            """.trimIndent()
                        )
                )
        )

        sigrunPensjonsgivendeInntektConsumer.hentPensjonsgivendeInntekt(
            PensjonsgivendeInntektRequest(inntektsaar = "2024", navPersonident = navPersonident)
        )

        wireMockServer.verify(
            WireMock.postRequestedFor(WireMock.urlPathEqualTo("/api/v1/pensjonsgivendeinntektforfolketrygden"))
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.containing(MediaType.APPLICATION_JSON_VALUE))
                // Personident i header er ikke tillatt (personvern) - derfor negativ assert.
                .withoutHeader("Nav-Personident")
                .withoutHeader("rettighetspakke")
                .withRequestBody(
                    WireMock.equalToJson(
                        """
                            {
                                "personident": "$navPersonident",
                                "inntektsaar": "2024",
                                "rettighetspakke": "navtrygdeavgift"
                            }
                        """.trimIndent()
                    )
                )
        )
    }

    @Test
    fun `skal håntere 404`() {
        wireMockServer.stubFor(
            createPostRequest("26468141637")
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
            createPostRequest("26468141639")
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
            createPostRequest("26468141640")
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
            createPostRequest("26468141641")
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
    fun `skal logge statuskode, body og Nav-Call-Id ved 403`() {
        wireMockServer.stubFor(
            createPostRequest("26468141642")
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
    }

    @Test
    fun `skal maskere personident i body og utelate ukjente response-headere`() {
        val navPersonident = "26468141643"
        wireMockServer.stubFor(
            createPostRequest(navPersonident)
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(400)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withHeader("Nav-Personident", navPersonident)
                        .withHeader("Set-Cookie", "session=hemmelig-token-abc123")
                        .withBody("""{"melding":"Ugyldig personidentifikator: $navPersonident for inntektsaar 2024"}""")
                )
        )

        val melding = fangErrorLogg {
            shouldThrow<WebClientResponseException> {
                sigrunPensjonsgivendeInntektConsumer.hentPensjonsgivendeInntekt(
                    PensjonsgivendeInntektRequest(inntektsaar = "2024", navPersonident = navPersonident)
                )
            }.statusCode.value() shouldBe 400
        }

        melding shouldContain "400"
        melding shouldContain "Ugyldig personidentifikator"
        melding shouldContain "***********"
        melding shouldNotContain navPersonident
        melding shouldNotContain "hemmelig-token-abc123"
    }

    @Test
    fun `skal avkorte lang feilbody`() {
        val navPersonident = "26468141644"
        val langBody = "<html>" + "x".repeat(20_000) + "</html>"
        wireMockServer.stubFor(
            createPostRequest(navPersonident)
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(502)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                        .withBody(langBody)
                )
        )

        val melding = fangErrorLogg {
            shouldThrow<WebClientResponseException> {
                sigrunPensjonsgivendeInntektConsumer.hentPensjonsgivendeInntekt(
                    PensjonsgivendeInntektRequest(inntektsaar = "2024", navPersonident = navPersonident)
                )
            }.statusCode.value() shouldBe 502
        }

        melding shouldContain "avkortet, ${langBody.length} tegn totalt"
        melding.length shouldBeLessThan 1000
    }

    @Test
    fun `skal behandle 3xx som feil og ikke som manglende body`() {
        wireMockServer.stubFor(
            createPostRequest("26468141645")
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(302)
                        .withHeader(HttpHeaders.LOCATION, "https://login.microsoftonline.com/")
                )
        )

        shouldThrow<WebClientResponseException> {
            sigrunPensjonsgivendeInntektConsumer.hentPensjonsgivendeInntekt(
                PensjonsgivendeInntektRequest(inntektsaar = "2024", navPersonident = "26468141645")
            )
        }.statusCode.value() shouldBe 302
    }

    @Test
    fun `skal ikke tolke uventet body fra 200 som tom inntekt`() {
        wireMockServer.stubFor(
            createPostRequest("26468141646")
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""{"melding":"Noe helt annet enn en inntektsrespons"}""")
                )
        )

        shouldThrow<IllegalStateException> {
            sigrunPensjonsgivendeInntektConsumer.hentPensjonsgivendeInntekt(
                PensjonsgivendeInntektRequest(inntektsaar = "2024", navPersonident = "26468141646")
            )
        }.message shouldContain "norskPersonidentifikator mangler"
    }

    private fun fangErrorLogg(block: () -> Unit): String {
        val logg = ListAppender<ILoggingEvent>().apply { start() }
        val logger = LoggerFactory.getLogger("no.nav.melosysskattehendelser.skatt.sigrun") as Logger
        logger.addAppender(logg)
        try {
            block()
        } finally {
            logger.detachAppender(logg)
        }
        return logg.list.single { it.level == Level.ERROR }.formattedMessage
    }

    private fun createPostRequest(navPersonident: String): MappingBuilder =
        WireMock.post(WireMock.urlPathEqualTo("/api/v1/pensjonsgivendeinntektforfolketrygden"))
            .withRequestBody(WireMock.matchingJsonPath("$.personident", WireMock.equalTo(navPersonident)))
}
