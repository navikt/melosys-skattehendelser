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
class SkattepliktigeControllerTest(
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
        personId1 = lagrePerson(
            "11111111111",
            periode(
                LocalDate.of(2025, 1, 1),
                "2024" to LocalDateTime.of(2026, 4, 1, 2, 0),
                "2025" to LocalDateTime.of(2026, 5, 1, 2, 0),
                "2025" to LocalDateTime.of(2026, 3, 1, 2, 0),
            ),
        )
        lagrePerson("22222222222", periode(LocalDate.of(2024, 6, 1), "2025" to LocalDateTime.of(2026, 9, 10, 2, 0)))
        lagrePerson("33333333333", periode(LocalDate.of(2025, 3, 1)))
        lagrePerson("44444444444", periode(LocalDate.of(2023, 1, 1), "2023" to LocalDateTime.of(2026, 9, 12, 2, 0)))
        personId5 = lagrePerson(
            "55555555555",
            periode(LocalDate.of(2025, 1, 1), "2025" to LocalDateTime.of(2026, 6, 1, 2, 0)),
            periode(LocalDate.of(2025, 7, 1), "2025" to LocalDateTime.of(2026, 7, 1, 2, 0)),
        )
    }

    @AfterEach
    fun tearDown() {
        personRepository.deleteAll()
    }

    @Test
    fun `FOM_AAR gir én rad per person med periode som starter i året, med publiseringene under disse periodene`() {
        val respons = hent("gjelderAar=2025", token())

        respons.statusCode() shouldBe 200
        val body = objectMapper.readTree(respons.body())
        body["antall"].asInt() shouldBe 2
        body["skattepliktige"].values().map { it["identifikator"].asString() } shouldBe listOf("11111111111", "55555555555")
        body["skattepliktige"].values().map { it["personId"]?.asLong() } shouldBe listOf(personId1, personId5)
        val rad = body["skattepliktige"][0]
        rad["gjelderPeriode"].asString() shouldBe "2025"
        rad["inntektsaar"].values().map(JsonNode::asString) shouldBe listOf("2024", "2025")
        rad["antallPubliseringer"].asInt() shouldBe 3
        rad["sisteHendelseTid"].asString() shouldBe "2026-05-01T02:00:00"
        body["skattepliktige"][1]["antallPubliseringer"].asInt() shouldBe 2
    }

    @Test
    fun `INNTEKTSAAR gir personer med publisering for inntektsåret, også når perioden startet året før`() {
        val respons = hent("gjelderAar=2025&aarFilter=INNTEKTSAAR", token())

        respons.statusCode() shouldBe 200
        objectMapper.readTree(respons.body())["skattepliktige"].values().map { it["identifikator"].asString() } shouldBe
            listOf("11111111111", "22222222222", "55555555555")
    }

    @Test
    fun `publisertEtter tar bare med personer med nyere publisering`() {
        val respons = hent("gjelderAar=2025&aarFilter=INNTEKTSAAR&publisertEtter=2026-09-08T00:00:00", token())

        respons.statusCode() shouldBe 200
        objectMapper.readTree(respons.body())["skattepliktige"].values().map { it["identifikator"].asString() } shouldBe
            listOf("22222222222")
    }

    @Test
    fun `avviser kall uten token`() {
        hent("gjelderAar=2025", token = null).statusCode() shouldBe 401
    }

    private data class PeriodeMedPubliseringer(val fom: LocalDate, val publiseringer: List<Pair<String, LocalDateTime>>)

    private fun periode(fom: LocalDate, vararg publiseringer: Pair<String, LocalDateTime>) =
        PeriodeMedPubliseringer(fom, publiseringer.toList())

    private var sekvensnummer = 0L

    private var personId1 = 0L
    private var personId5 = 0L

    private fun lagrePerson(ident: String, vararg perioder: PeriodeMedPubliseringer): Long {
        val person = Person(ident = ident)
        perioder.forEach { (fom, publiseringer) ->
            val periode = Periode(person = person, fom = fom, tom = fom.plusYears(1))
            publiseringer.forEach { (inntektsår, tid) ->
                periode.publiseringsHistorikk.add(
                    PubliseringsHistorikk(periode = periode, inntektÅr = inntektsår, sekvensnummer = ++sekvensnummer, sisteHendelseTid = tid)
                )
            }
            person.perioder.add(periode)
        }
        return personRepository.save(person).id
    }

    private fun token(): String =
        mockOAuth2Server.issueToken(
            "aad",
            "melosys",
            DefaultOAuth2TokenCallback(
                issuerId = "aad",
                subject = "melosys",
                audience = listOf("skattehendelser-test"),
                claims = mapOf("azp_name" to "dev-fss:teammelosys:melosys"),
            )
        ).serialize()

    private fun hent(query: String, token: String?): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/api/admin/skattepliktige?$query"))
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .GET()
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
