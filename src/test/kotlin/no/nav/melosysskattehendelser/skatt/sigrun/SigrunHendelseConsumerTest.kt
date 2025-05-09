package no.nav.melosysskattehendelser.skatt.sigrun

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import no.nav.melosysskattehendelser.skatt.Hendelse
import no.nav.melosysskattehendelser.skatt.HendelseRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SigrunHendelseConsumerTest {

    private val wireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort()).apply {
        start()
    }
    private val sigrunHendelseConsumer = SigrunHendelseConsumer(
        WebClient.builder()
            .baseUrl("http://localhost:${wireMockServer.port()}")
            .build()
    )

    @AfterAll
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun `skal hente liste med hendelser`() {
        wireMockServer.stubFor(
            createGetRequest()
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(
                            """
                            {
                              "hendelser": [
                                {
                                  "gjelderPeriode": "2023",
                                  "identifikator": "123456",
                                  "sekvensnummer": 0,
                                  "somAktoerid": true
                                },
                                {
                                  "gjelderPeriode": "2023",
                                  "identifikator": "456789",
                                  "sekvensnummer": 1,
                                  "somAktoerid": true,
                                  "hendelsetype": null
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                )
        )

        val hendelseListe = sigrunHendelseConsumer.hentHendelseListe(
            HendelseRequest(
                0,
                1000,
                false
            )
        )

        hendelseListe
            .shouldContainExactly(
                Hendelse("2023", "123456", 0, true, hendelsetype = "ukjent"),
                Hendelse("2023", "456789", 1, true, hendelsetype = "ukjent")
            )
    }

    @Test
    fun `hente liste med hendelser - kaster exception ved manglende felt`() {
        wireMockServer.stubFor(
            createGetRequest()
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""{ "foo": [] }""")
                )
        )

        shouldThrow<IllegalStateException> {
            sigrunHendelseConsumer.hentHendelseListe(
                HendelseRequest(
                    0,
                    1000,
                    false
                )
            )
        }.message shouldBe "Response body inneholder ikke 'hendelser', var: {foo=[]}"
    }

    @Test
    fun `hente liste med hendelser - kaster exception ved manglende body`() {
        wireMockServer.stubFor(
            createGetRequest()
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                )
        )

        shouldThrow<IllegalStateException> {
            sigrunHendelseConsumer.hentHendelseListe(
                HendelseRequest(
                    0,
                    1000,
                    false
                )
            )
        }.message shouldBe "Ingen body - kunne ikke hente hendelser"
    }

    private fun createGetRequest(): MappingBuilder =
        WireMock.get(WireMock.urlPathEqualTo("/api/v1/pensjonsgivendeinntektforfolketrygden/hendelser"))
            .withQueryParam("fraSekvensnummer", WireMock.equalTo("0"))
            .withQueryParam("antall", WireMock.equalTo("1000"))
            .withHeader("bruk-aktoerid", WireMock.equalTo("false"))
}
