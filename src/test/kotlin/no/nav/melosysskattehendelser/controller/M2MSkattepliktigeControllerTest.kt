package no.nav.melosysskattehendelser.controller

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.melosysskattehendelser.PostgresTestContainerBase
import no.nav.melosysskattehendelser.domain.Periode
import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.domain.PubliseringsHistorikk
import no.nav.melosysskattehendelser.melosys.KafkaConfig
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.LocalDateTime

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableMockOAuth2Server
class M2MSkattepliktigeControllerTest(
    @Autowired private val personRepository: PersonRepository,
    @Autowired private val mockOAuth2Server: MockOAuth2Server,
    @Autowired private val objectMapper: ObjectMapper,
    @LocalServerPort private val port: Int,
) : PostgresTestContainerBase() {

    @TestConfiguration
    class Config {
        @Bean // Slipper å kjøre med @EmbeddedKafka
        fun kafkaConfig() = mockk<KafkaConfig>(relaxed = true)
    }

    private val httpClient = HttpClient.newHttpClient()

    @BeforeEach
    fun setUp() {
        personRepository.deleteAll()
        lagrePerson("11111111111", fom = LocalDate.of(2025, 1, 1), inntektsaar = "2025", tid = LocalDateTime.of(2026, 5, 1, 2, 0))
        lagrePerson("22222222222", fom = LocalDate.of(2024, 6, 1), inntektsaar = "2025", tid = LocalDateTime.of(2026, 9, 10, 2, 0))
        lagrePerson("33333333333", fom = LocalDate.of(2025, 3, 1), inntektsaar = null, tid = null)
    }

    @AfterEach
    fun tearDown() {
        personRepository.deleteAll()
    }

    @Test
    fun `FOM_AAR gir personer med periode som starter i året, med historikk fra alle publiseringer`() {
        val respons = hent("gjelderAar=2025", token(APP, TILLATT_KLIENT))

        respons.statusCode() shouldBe 200
        val body = objectMapper.readTree(respons.body())
        body["antall"].asInt() shouldBe 1
        val rad = body["skattepliktige"][0]
        rad["gjelderPeriode"].asString() shouldBe "2025"
        rad["identifikator"].asString() shouldBe "11111111111"
        rad["inntektsaar"].values().map(JsonNode::asString) shouldBe listOf("2024", "2025")
        rad["antallPubliseringer"].asInt() shouldBe 2
        rad["sisteHendelseTid"].asString() shouldBe "2026-05-01T02:00:00"
    }

    @Test
    fun `INNTEKTSAAR gir personer med publisering for inntektsåret, også når perioden startet året før`() {
        val respons = hent("gjelderAar=2025&aarFilter=INNTEKTSAAR", token(APP, TILLATT_KLIENT))

        respons.statusCode() shouldBe 200
        objectMapper.readTree(respons.body())["skattepliktige"].values().map { it["identifikator"].asString() } shouldBe
            listOf("11111111111", "22222222222")
    }

    @Test
    fun `publisertEtter tar bare med personer med nyere publisering`() {
        val respons = hent("gjelderAar=2025&aarFilter=INNTEKTSAAR&publisertEtter=2026-09-08T00:00:00", token(APP, TILLATT_KLIENT))

        respons.statusCode() shouldBe 200
        objectMapper.readTree(respons.body())["skattepliktige"].values().map { it["identifikator"].asString() } shouldBe
            listOf("22222222222")
    }

    @Test
    fun `avviser brukertoken fra tillatt klient`() {
        hent("gjelderAar=2025", token(idtyp = null, azpName = TILLATT_KLIENT)).statusCode() shouldBe 403
    }

    @Test
    fun `avviser apptoken fra annen klient`() {
        hent("gjelderAar=2025", token(APP, "dev-gcp:teammelosys:annen-app")).statusCode() shouldBe 403
    }

    @Test
    fun `avviser kall uten token`() {
        hent("gjelderAar=2025", token = null).statusCode() shouldBe 401
    }

    private fun lagrePerson(ident: String, fom: LocalDate, inntektsaar: String?, tid: LocalDateTime?) {
        val person = Person(ident = ident)
        val periode = Periode(person = person, fom = fom, tom = fom.plusYears(1))
        person.perioder.add(periode)
        if (inntektsaar != null && tid != null) {
            periode.publiseringsHistorikk.add(
                PubliseringsHistorikk(periode = periode, inntektÅr = "2024", sekvensnummer = ident.take(4).toLong(), sisteHendelseTid = tid.minusMonths(1))
            )
            periode.publiseringsHistorikk.add(
                PubliseringsHistorikk(periode = periode, inntektÅr = inntektsaar, sekvensnummer = ident.take(5).toLong(), sisteHendelseTid = tid)
            )
        }
        personRepository.save(person)
    }

    private fun token(idtyp: String?, azpName: String): String =
        mockOAuth2Server.issueToken(
            "aad",
            "melosys",
            DefaultOAuth2TokenCallback(
                issuerId = "aad",
                subject = "melosys",
                audience = listOf("skattehendelser-test"),
                claims = buildMap {
                    put("azp_name", azpName)
                    idtyp?.let { put("idtyp", it) }
                },
            )
        ).serialize()

    private fun hent(query: String, token: String?): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/m2m/api/skattepliktige?$query"))
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .GET()
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        private const val APP = "app"
        private const val TILLATT_KLIENT = "dev-fss:teammelosys:melosys"
    }
}
