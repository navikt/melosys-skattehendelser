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
    @Value("\${admin.api-key}") private val apiNøkkel: String,
    @Value("\${admin.driftsgruppe}") private val driftsgruppeId: String,
) : PostgresTestContainerBase() {

    @TestConfiguration
    class Config {
        @Bean // Slipper å kjøre med @EmbeddedKafka
        fun kafkaConfig() = mockk<KafkaConfig>(relaxed = true)
    }

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `personkall med driftsgruppe og nøkkel får tilgang`() {
        hentPersoner(personToken(grupper = listOf(driftsgruppeId))).statusCode() shouldBe 200
    }

    @Test
    fun `personkall uten driftsgruppe avvises med forklaring, selv med riktig nøkkel`() {
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
    fun `maskinkall slipper gjennom gruppesjekken`() {
        hentPersoner(maskinToken()).statusCode() shouldBe 200
    }

    @Test
    fun `nøkkelen kreves fortsatt for personkall med driftsgruppe`() {
        val token = personToken(grupper = listOf(driftsgruppeId))

        hentPersoner(token, nøkkel = "feil-nøkkel").statusCode() shouldBe 401
        hentPersoner(token, nøkkel = null).statusCode() shouldBe 401
    }

    @Test
    fun `nøkkelen kreves fortsatt for maskinkall`() {
        hentPersoner(maskinToken(), nøkkel = null).statusCode() shouldBe 401
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

    private fun personToken(grupper: List<String>?): String =
        token(buildMap<String, Any> {
            put("NAVident", "Z999999")
            grupper?.let { put("groups", it) }
        })

    private fun maskinToken(): String = token(mapOf("idtyp" to "app"))

    private fun token(claims: Map<String, Any>, audience: String = "skattehendelser-test"): String =
        mockOAuth2Server.issueToken(
            "aad",
            "melosys-console",
            DefaultOAuth2TokenCallback(
                issuerId = "aad",
                subject = "melosys-console",
                audience = listOf(audience),
                claims = claims,
            )
        ).serialize()

    private fun hentPersoner(token: String?, nøkkel: String? = apiNøkkel): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/admin/person?max=1"))
            .apply {
                token?.let { header("Authorization", "Bearer $it") }
                nøkkel?.let { header(ApiKeyInterceptor.API_KEY_HEADER, it) }
            }
            .GET()
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        private const val ANNEN_GRUPPE = "00000000-0000-0000-0000-000000000002"
    }
}
