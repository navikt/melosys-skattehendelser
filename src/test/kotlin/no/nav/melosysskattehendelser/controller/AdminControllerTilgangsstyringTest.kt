package no.nav.melosysskattehendelser.controller

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.melosysskattehendelser.PostgresTestContainerBase
import no.nav.melosysskattehendelser.melosys.KafkaConfig
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableMockOAuth2Server
class AdminControllerTilgangsstyringTest(
    @Autowired private val mockOAuth2Server: MockOAuth2Server,
    @LocalServerPort private val port: Int,
    @Value("\${admin.driftsgruppe}") private val driftsgruppeId: String,
    @Value("\${admin.console-klient-id}") private val consoleKlientId: String,
) : PostgresTestContainerBase() {

    @TestConfiguration
    class Config {
        @Bean // Slipper å kjøre med @EmbeddedKafka
        fun kafkaConfig() = mockk<KafkaConfig>(relaxed = true)
    }

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `personkall fra Console med driftsgruppe får tilgang`() {
        hentPersoner(personToken(grupper = listOf(driftsgruppeId))).statusCode() shouldBe 200
    }

    @Test
    fun `maskinkall fra Console får tilgang`() {
        hentPersoner(maskinToken()).statusCode() shouldBe 200
    }

    @Test
    fun `personkall fra annen klient avvises, selv med driftsgruppe`() {
        val respons = hentPersoner(personToken(grupper = listOf(driftsgruppeId), klientId = ANNEN_KLIENT))

        respons.statusCode() shouldBe 403
        respons.body() shouldBe AdminTilgangInterceptor.UKJENT_KLIENT
    }

    @Test
    fun `maskinkall fra annen klient avvises`() {
        val respons = hentPersoner(maskinToken(klientId = ANNEN_KLIENT))

        respons.statusCode() shouldBe 403
        respons.body() shouldBe AdminTilgangInterceptor.UKJENT_KLIENT
    }

    @Test
    fun `personkall uten driftsgruppe avvises med forklaring`() {
        val respons = hentPersoner(personToken(grupper = listOf(ANNEN_GRUPPE)))

        respons.statusCode() shouldBe 403
        respons.body() shouldBe AdminTilgangInterceptor.MANGLER_DRIFTSGRUPPE
    }

    @Test
    fun `personkall uten groups-claim avvises`() {
        hentPersoner(personToken(grupper = null)).statusCode() shouldBe 403
    }

    @Test
    fun `token med annen idtyp enn app regnes som personkall`() {
        hentPersoner(token(mapOf("idtyp" to "user"))).statusCode() shouldBe 403
    }

    @Test
    fun `nøkkelheader påvirker ikke svaret`() {
        val fraConsole = personToken(grupper = listOf(driftsgruppeId))
        val fraAnnenKlient = personToken(grupper = listOf(driftsgruppeId), klientId = ANNEN_KLIENT)

        hentPersoner(fraConsole, nøkkel = "feil-nøkkel").statusCode() shouldBe 200
        hentPersoner(fraAnnenKlient, nøkkel = "tidligere-riktig-nøkkel").statusCode() shouldBe 403
    }

    @Test
    fun `kall uten token avvises`() {
        hentPersoner(token = null).statusCode() shouldBe 401
    }

    @Test
    fun `token med feil audience avvises`() {
        val token = token(mapOf("groups" to listOf(driftsgruppeId)), audience = "annen-app")

        hentPersoner(token).statusCode() shouldBe 401
    }

    private fun personToken(grupper: List<String>?, klientId: String = consoleKlientId): String =
        token(
            buildMap<String, Any> {
                put("NAVident", "Z999999")
                grupper?.let { put("groups", it) }
            },
            klientId = klientId,
        )

    private fun maskinToken(klientId: String = consoleKlientId): String =
        token(mapOf("idtyp" to "app"), klientId = klientId)

    // mock-oauth2-server setter azp til klient-ID-en tokenet utstedes til
    private fun token(
        claims: Map<String, Any>,
        klientId: String = consoleKlientId,
        audience: String = "skattehendelser-test",
    ): String =
        mockOAuth2Server.issueToken(
            "aad",
            klientId,
            DefaultOAuth2TokenCallback(
                issuerId = "aad",
                subject = klientId,
                audience = listOf(audience),
                claims = claims,
            )
        ).serialize()

    private fun hentPersoner(token: String?, nøkkel: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/admin/person?max=1"))
            .apply {
                token?.let { header("Authorization", "Bearer $it") }
                nøkkel?.let { header(GAMMEL_NØKKELHEADER, it) }
            }
            .GET()
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        private const val ANNEN_GRUPPE = "00000000-0000-0000-0000-000000000002"
        private const val ANNEN_KLIENT = "annen-klient"
        // Console sender headeren til fase 5. Den skal ignoreres.
        private const val GAMMEL_NØKKELHEADER = "X-SKATTEHENDELSER-ADMIN-APIKEY"
    }
}
