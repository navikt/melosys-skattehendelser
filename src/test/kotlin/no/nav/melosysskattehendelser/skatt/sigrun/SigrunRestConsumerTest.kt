package no.nav.melosysskattehendelser.skatt.sigrun

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import no.nav.melosysskattehendelser.skatt.Hendelse
import no.nav.melosysskattehendelser.skatt.HendelseRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class SigrunRestConsumerTest {

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
            WireMock.get("/api/skatteoppgjoer/hendelser")
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(
                            """
                            [
                              {
                                "gjelderPeriode": "2023",
                                "identifikator": "123456",
                                "sekvensnummer": 0,
                                "somAktoerid": true,
                                "hendelsetype":"ny"
                              },
                              {
                                "gjelderPeriode": "2023",
                                "identifikator": "456789",
                                "sekvensnummer": 1,
                                "somAktoerid": true,
                                "hendelsetype":"ny"
                              }
                            ]
                        """.trimIndent()
                        )
                )
        )
        val request = HendelseRequest(
            0,
            1000,
            true
        )

        sigrunRestConsumer.hentHendelseListe(request).run {
            shouldNotBeNull()
            shouldHaveSize(2)
            shouldContainExactlyInAnyOrder(
                Hendelse("2023", "123456", 0, true, hendelsetype = "ny"),
                Hendelse("2023", "456789", 1, true, hendelsetype = "ny")
            )
        }

        wireMockServer.verify(
            WireMock.getRequestedFor(WireMock.urlEqualTo("/api/skatteoppgjoer/hendelser"))
                .withHeader("x-sekvensnummer-fra", WireMock.equalTo("0"))
                .withHeader("x-antall", WireMock.equalTo("1000"))
                .withHeader("x-bruk-aktoerid", WireMock.equalTo("true"))
        )

    }


}