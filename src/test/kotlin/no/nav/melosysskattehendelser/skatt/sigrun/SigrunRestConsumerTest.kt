package no.nav.melosysskattehendelser.skatt.sigrun

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.matchers.collections.shouldContainExactly
import no.nav.melosysskattehendelser.skatt.Hendelse
import no.nav.melosysskattehendelser.skatt.HendelseRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SigrunRestConsumerTest {

    private val wireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort()).apply {
        start()
    }
    private val sigrunRestConsumer = SigrunRestConsumer(
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
            WireMock.get(WireMock.urlPathEqualTo("/api/v1/pensjonsgivendeinntektforfolketrygden/hendelser"))
                .withQueryParam("fraSekvensnummer", WireMock.equalTo("0"))
                .withQueryParam("antall", WireMock.equalTo("1000"))
                .withHeader("bruk-aktoerid", WireMock.equalTo("false"))
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
                                  "somAktoerid": true,
                                  "hendelsetype": "ny"
                                },
                                {
                                  "gjelderPeriode": "2023",
                                  "identifikator": "456789",
                                  "sekvensnummer": 1,
                                  "somAktoerid": true,
                                  "hendelsetype": "ny"
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                )
        )

        val hendelseListe = sigrunRestConsumer.hentHendelseListe(
            HendelseRequest(
                0,
                1000,
                false
            )
        )

        hendelseListe
            .shouldContainExactly(
                Hendelse("2023", "123456", 0, true, hendelsetype = "ny"),
                Hendelse("2023", "456789", 1, true, hendelsetype = "ny")
            )
    }
}
