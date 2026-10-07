package no.nav.melosysskattehendelser.controller

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.melosysskattehendelser.PostgresTestContainerBase
import no.nav.melosysskattehendelser.melosys.KafkaConfig
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
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
    @Autowired @Qualifier("requestMappingHandlerMapping") private val handlerMapping: RequestMappingHandlerMapping,
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

    // --- Alle registrerte admin-endepunkter ---
    //
    // Endepunktene hentes fra Spring, så nye admin-kontrollere dekkes uten at testene må oppdateres.
    // assertSoftly viser alle endepunkter som feiler, ikke bare det første.

    @Test
    fun `kall uten token avvises på alle registrerte admin-endepunkter`() {
        val endepunkter = registrerteAdminEndepunkter()

        assertSoftly {
            endepunkter.forEach { endepunkt ->
                // Bare status: både AdminTilgangInterceptor og @Protected kan svare 401, og begge er riktige
                withClue(endepunkt) {
                    kall(endepunkt, token = null).statusCode() shouldBe 401
                }
            }
        }
    }

    @Test
    fun `kall fra annen klient avvises på alle registrerte admin-endepunkter`() {
        val endepunkter = registrerteAdminEndepunkter()
        val token = personToken(grupper = listOf(driftsgruppeId), klientId = ANNEN_KLIENT)

        assertSoftly {
            endepunkter.forEach { endepunkt ->
                withClue(endepunkt) {
                    val respons = kall(endepunkt, token)
                    respons.statusCode() shouldBe 403
                    respons.body() shouldBe AdminTilgangInterceptor.UKJENT_KLIENT
                }
            }
        }
    }

    @Test
    fun `personkall uten driftsgruppe avvises på alle registrerte admin-endepunkter`() {
        val endepunkter = registrerteAdminEndepunkter()
        val token = personToken(grupper = emptyList())

        assertSoftly {
            endepunkter.forEach { endepunkt ->
                withClue(endepunkt) {
                    val respons = kall(endepunkt, token)
                    respons.statusCode() shouldBe 403
                    respons.body() shouldBe AdminTilgangInterceptor.MANGLER_DRIFTSGRUPPE
                }
            }
        }
    }

    private data class Endepunkt(val metode: String, val mønster: String) {
        // Interceptoren avviser før argumentene leses, så stivariablene trenger bare å matche mønsteret
        val sti = mønster.replace(Regex("\\{[^}]+}"), "1")

        override fun toString() = "$metode $mønster"
    }

    private fun registrerteAdminEndepunkter(): List<Endepunkt> {
        val endepunkter = handlerMapping.handlerMethods.keys.flatMap { info ->
            val metoder = info.methodsCondition.methods.ifEmpty { setOf(RequestMethod.GET) }
            info.patternValues
                .filter { it.startsWith("/admin/") }
                .flatMap { mønster -> metoder.map { Endepunkt(it.name, mønster) } }
        }

        // Vakt mot falsk grønn: finner oppslaget ingen endepunkter, kjører forEach ingen assertions,
        // og testene passerer uten å ha sjekket noe. Ett GET- og ett POST-endepunkt viser at begge dekkes.
        endepunkter.map { it.toString() }.shouldContainAll(
            "GET /admin/person",
            "POST /admin/hendelseprosessering/start",
        )
        return endepunkter
    }

    private fun kall(endepunkt: Endepunkt, token: String?): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port${endepunkt.sti}"))
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .header("Content-Type", "application/json")
            .method(endepunkt.metode, HttpRequest.BodyPublishers.noBody())
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
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
