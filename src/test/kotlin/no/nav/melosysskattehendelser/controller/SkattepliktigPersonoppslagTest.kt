package no.nav.melosysskattehendelser.controller

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import com.github.benmanes.caffeine.cache.Cache
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.http.ResponseDefinition
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.mockk
import no.nav.melosysskattehendelser.LoggingTestUtils
import no.nav.melosysskattehendelser.PostgresTestContainerBase
import no.nav.melosysskattehendelser.domain.PensjonsgivendeInntekt
import no.nav.melosysskattehendelser.domain.PensjonsgivendeInntektRepository
import no.nav.melosysskattehendelser.domain.Periode
import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.domain.PubliseringsHistorikk
import no.nav.melosysskattehendelser.melosys.KafkaConfig
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektRequest
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektResponse
import no.nav.melosysskattehendelser.skatt.sigrun.PENSJONSGIVENDE_INNTEKT_CACHE
import no.nav.melosysskattehendelser.skatt.sigrun.SigrunPensjonsgivendeInntektConsumer
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList
import javax.sql.DataSource

/**
 * Går gjennom hele Spring-konteksten: ekte token, Postgres og den ekte Sigrun-klienten (med cachen) mot WireMock.
 */
@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["sigrun.tidsfrist-utenom-cache=1s"],
)
@EnableMockOAuth2Server
class SkattepliktigPersonoppslagTest(
    @Autowired private val sigrunConsumer: SigrunPensjonsgivendeInntektConsumer,
    @Autowired private val personRepository: PersonRepository,
    @Autowired private val pensjonsgivendeInntektRepository: PensjonsgivendeInntektRepository,
    @Autowired private val mockOAuth2Server: MockOAuth2Server,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val cacheManager: CacheManager,
    @Autowired private val dataSource: DataSource,
    @LocalServerPort private val port: Int,
) : PostgresTestContainerBase() {

    @TestConfiguration
    class Config {
        @Bean // Slipper å kjøre med @EmbeddedKafka
        fun kafkaConfig() = mockk<KafkaConfig>(relaxed = true)

        @Bean // Samme navn som i SigrunConsumerConfig, så den erstattes; uten token mot WireMock
        fun sigrunPensjonsgivendeInntektConsumer() =
            SigrunPensjonsgivendeInntektConsumer(WebClient.builder().baseUrl("http://localhost:${sigrun.port()}").build())
    }

    private val httpClient = HttpClient.newHttpClient()

    @BeforeEach
    fun setUp() {
        pensjonsgivendeInntektRepository.deleteAll()
        personRepository.deleteAll()
        sigrun.resetAll()
        cacheManager.getCache(PENSJONSGIVENDE_INNTEKT_CACHE)?.clear()
    }

    @AfterEach
    fun tearDown() {
        pensjonsgivendeInntektRepository.deleteAll()
        personRepository.deleteAll()
    }

    @Test
    fun `ukjent ident gir tom liste og ingen kall til Sigrun`() {
        val respons = slåOpp(forespørsel(2025, UKJENT to true))

        respons.statusCode() shouldBe 200
        objectMapper.readTree(respons.body()).values().toList().shouldBeEmpty()
        sigrun.findAll(WireMock.anyRequestedFor(WireMock.anyUrl())).shouldBeEmpty()
    }

    @Test
    fun `to identer til to personer gir to rader, og registrertPaaGjeldendeIdent følger gjeldende-flagget`() {
        val gjeldende = lagrePerson(FNR, periode(LocalDate.of(2023, 1, 1)))
        val gammel = lagrePerson(DNR, periode(LocalDate.of(2023, 1, 1)))

        val respons = slåOpp(forespørsel(2025, FNR to true, DNR to false))

        respons.statusCode() shouldBe 200
        val rader = objectMapper.readTree(respons.body()).values().associateBy { it["personId"].asLong() }
        rader.keys shouldBe setOf(gjeldende, gammel)
        rader.getValue(gjeldende)["registrertPaaGjeldendeIdent"].asBoolean() shouldBe true
        rader.getValue(gammel)["registrertPaaGjeldendeIdent"].asBoolean() shouldBe false
    }

    @Test
    fun `svaret har perioder, publiseringer og lagrede inntektsår delt på slettet`() {
        val personId = lagrePerson(
            FNR,
            periode(LocalDate.of(2024, 6, 1), "2024" to LocalDateTime.of(2025, 9, 1, 2, 0)),
            periode(LocalDate.of(2023, 1, 1)),
        )
        lagreInntekt(FNR, "2024", slettet = false)
        lagreInntekt(FNR, "2023", slettet = true)

        val rad = objectMapper.readTree(slåOpp(forespørsel(2024, FNR to true)).body()).single()

        rad["personId"].asLong() shouldBe personId
        rad["inntektsaarMedInntekt"].strenger() shouldBe listOf("2024")
        rad["inntektsaarSlettet"].strenger() shouldBe listOf("2023")
        rad["sigrunHarInntektForAaret"].isNull shouldBe true
        rad["perioder"].values().map { it["fom"].asString() } shouldBe listOf("2023-01-01", "2024-06-01")
        rad["perioder"][1]["tom"].asString() shouldBe "2025-06-01"
        rad["perioder"][1]["publiseringer"].single().let {
            it["inntektsaar"].asString() shouldBe "2024"
            it["sisteHendelseTid"].asString() shouldBe "2025-09-01T02:00:00"
        }
    }

    @Test
    fun `Sigrun slås opp bare for person med periode i året uten publisering og uten inntekt for året`() {
        val skalSpørres = lagrePerson("10000000001", periode(LocalDate.of(2025, 1, 1)))
        val publisert = lagrePerson("10000000002", periode(LocalDate.of(2025, 1, 1), "2025" to LocalDateTime.of(2026, 5, 1, 2, 0)))
        val medInntekt = lagrePerson("10000000003", periode(LocalDate.of(2025, 1, 1)))
        lagreInntekt("10000000003", "2025", slettet = false)
        val utenPeriode = lagrePerson("10000000004", periode(LocalDate.of(2022, 1, 1)))
        val baresSlettet = lagrePerson("10000000005", periode(LocalDate.of(2025, 1, 1)))
        lagreInntekt("10000000005", "2025", slettet = true)
        sigrunSvarer("10000000001", 200)
        sigrunSvarer("10000000005", 404)

        val rader = objectMapper.readTree(
            slåOpp(forespørsel(2025, *(1..5).map { "1000000000$it" to true }.toTypedArray())).body()
        ).values().associate { it["personId"].asLong() to it["sigrunHarInntektForAaret"] }

        rader.getValue(skalSpørres).asBoolean() shouldBe true
        rader.getValue(baresSlettet).asBoolean() shouldBe false
        listOf(publisert, medInntekt, utenPeriode).forEach { rader.getValue(it).isNull shouldBe true }
        sigrunSpurtFor() shouldBe listOf("10000000001", "10000000005")
    }

    @Test
    fun `publisering og inntekt for et annet år hindrer ikke oppslag i Sigrun`() {
        lagrePerson(FNR, periode(LocalDate.of(2024, 6, 1), "2024" to LocalDateTime.of(2025, 9, 1, 2, 0)))
        lagreInntekt(FNR, "2024", slettet = false)
        sigrunSvarer(FNR, 200)

        val rad = objectMapper.readTree(slåOpp(forespørsel(2025, FNR to true)).body()).single()

        rad["sigrunHarInntektForAaret"].asBoolean() shouldBe true
        sigrunSpurtFor() shouldBe listOf(FNR)
    }

    @Test
    fun `publisering for året på den tidligste av to perioder hindrer oppslag i Sigrun`() {
        val publisering = "2025" to LocalDateTime.of(2026, 5, 1, 2, 0)
        // Periodene lagres i hver sin rekkefølge, så testen ikke avhenger av rekkefølgen de lastes i
        val tidligstFørst = lagrePerson(FNR, periode(LocalDate.of(2024, 6, 1), publisering), periode(LocalDate.of(2025, 1, 1)))
        val tidligstSist = lagrePerson(DNR, periode(LocalDate.of(2025, 1, 1)), periode(LocalDate.of(2024, 6, 1), publisering))
        sigrunSvarer(FNR, 200)
        sigrunSvarer(DNR, 200)

        val rader = objectMapper.readTree(slåOpp(forespørsel(2025, FNR to true, DNR to true)).body())
            .values().associate { it["personId"].asLong() to it["sigrunHarInntektForAaret"] }

        listOf(tidligstFørst, tidligstSist).forEach { rader.getValue(it).isNull shouldBe true }
        sigrunSpurtFor().shouldBeEmpty()
    }

    @Test
    fun `404 fra Sigrun gir false, og ingenting lagres`() {
        lagrePerson(FNR, periode(LocalDate.of(2025, 1, 1)))
        sigrunSvarer(FNR, 404)

        val rad = objectMapper.readTree(slåOpp(forespørsel(2025, FNR to true)).body()).single()

        rad["sigrunHarInntektForAaret"].asBoolean() shouldBe false
        pensjonsgivendeInntektRepository.findAll().toList().shouldBeEmpty()
    }

    @Test
    fun `feil fra Sigrun gir null for personen, og svaret er fortsatt 200`() {
        val med500 = lagrePerson(FNR, periode(LocalDate.of(2025, 1, 1)))
        val brutt = lagrePerson(DNR, periode(LocalDate.of(2025, 1, 1)))
        sigrunSvarer(FNR, 500)
        sigrun.stubFor(sigrunKall(DNR).willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

        val respons = slåOpp(forespørsel(2025, FNR to true, DNR to false))

        respons.statusCode() shouldBe 200
        val rader = objectMapper.readTree(respons.body()).values().associate { it["personId"].asLong() to it["sigrunHarInntektForAaret"] }
        rader.getValue(med500).isNull shouldBe true
        rader.getValue(brutt).isNull shouldBe true
        sigrunSpurtFor() shouldBe listOf(FNR, DNR)
    }

    @Test
    fun `oppslaget går utenom cachen nattjobben bruker og etterlater ingenting i den`() {
        lagrePerson(FNR, periode(LocalDate.of(2025, 1, 1)))
        sigrunSvarer(FNR, 200)
        val cache = cacheManager.getCache(PENSJONSGIVENDE_INNTEKT_CACHE)!!
        val nøkkel = SigrunPensjonsgivendeInntektConsumer.cachenokkel(PensjonsgivendeInntektRequest("2025", FNR))
        cache.put(nøkkel, PensjonsgivendeInntektResponse(slettet = true, norskPersonidentifikator = FNR, inntektsaar = "2025"))

        val rad = objectMapper.readTree(slåOpp(forespørsel(2025, FNR to true)).body()).single()

        rad["sigrunHarInntektForAaret"].asBoolean() shouldBe true
        sigrun.findAll(WireMock.anyRequestedFor(WireMock.anyUrl())).size shouldBe 1
        cache.get(nøkkel).shouldBeNull()
    }

    @Test
    fun `oppslaget fjerner svaret nattjobben la i cachen, under nøkkelen Cacheable bruker`() {
        lagrePerson(FNR, periode(LocalDate.of(2025, 1, 1)))
        sigrunSvarer(FNR, 200)
        // Gjennom cache-proxyen, som nattjobben: svaret lagres under nøkkelen @Cacheable lager
        sigrunConsumer.hentPensjonsgivendeInntekt(PensjonsgivendeInntektRequest(inntektsaar = "2025", navPersonident = FNR))
        val caffeine = cacheManager.getCache(PENSJONSGIVENDE_INNTEKT_CACHE)!!.nativeCache as Cache<*, *>
        caffeine.estimatedSize() shouldBe 1

        slåOpp(forespørsel(2025, FNR to true)).statusCode() shouldBe 200

        caffeine.cleanUp()
        caffeine.estimatedSize() shouldBe 0
    }

    @Test
    fun `Sigrun som ikke svarer innen tidsfristen gir null for personen, og svaret er fortsatt 200`() {
        val personer = listOf(FNR, DNR).map { lagrePerson(it, periode(LocalDate.of(2025, 1, 1))) }
        listOf(FNR, DNR).forEach {
            sigrun.stubFor(sigrunKall(it).willReturn(WireMock.aResponse().withStatus(404).withFixedDelay(SIGRUN_FORSINKELSE_MS)))
        }

        val start = System.nanoTime()
        val respons = slåOpp(forespørsel(2025, FNR to true, DNR to false))
        val bruktMs = (System.nanoTime() - start) / 1_000_000

        // Tidsfristen er 1 s i denne testen: to oppslag tar rundt 2 s, ikke 2 × forsinkelsen
        bruktMs shouldBeLessThan SIGRUN_FORSINKELSE_MS.toLong()
        respons.statusCode() shouldBe 200
        val rader = objectMapper.readTree(respons.body()).values().associate { it["personId"].asLong() to it["sigrunHarInntektForAaret"] }
        personer.forEach { rader.getValue(it).isNull shouldBe true }
    }

    @Test
    fun `annen klient gir 403 og uten token 401`() {
        slåOpp(forespørsel(2025, FNR to true), token(azpName = "dev-gcp:teammelosys:melosys-console")).statusCode() shouldBe 403
        slåOpp(forespørsel(2025, FNR to true), token(azpName = null)).statusCode() shouldBe 403
        slåOpp(forespørsel(2025, FNR to true), token = null).statusCode() shouldBe 401
    }

    @Test
    fun `annen klient med ugyldig body gir 403 og avvisningslinjen, uten ident`() {
        val (svar, logg) = LoggingTestUtils.withLogCapture { logg ->
            listOf("{nope", UGYLDIG_GJELDENDE, "").map { slåOpp(it, token(azpName = "x")) } to logg.toList()
        }

        svar.map { it.statusCode() } shouldBe listOf(403, 403, 403)
        val loggtekst = logg.joinToString("\n") { it.fullTekst() }
        loggtekst shouldContain "Personoppslag avvist for klient=x"
        loggtekst shouldNotContain "Ugyldig body"
        (svar.map { it.body() } + loggtekst).forEach { ELLEVE_SIFRE.find(it)?.value.shouldBeNull() }
    }

    @Test
    fun `ugyldig body gir 400 som JSON, uten ident`() {
        val (svar, logg) = LoggingTestUtils.withLogCapture { logg ->
            listOf("{nope", UGYLDIG_GJELDENDE, "").map { slåOpp(it, accept = "application/json") } to logg.toList()
        }

        svar.forEach { respons ->
            respons.statusCode() shouldBe 400
            respons.headers().firstValue("Content-Type").get() shouldContain "application/json"
            objectMapper.readTree(respons.body()).let {
                it["status"].asInt() shouldBe 400
                it["error"].asString() shouldBe "Bad Request"
                it["path"].asString() shouldBe "/api/admin/skattepliktige/personoppslag"
            }
        }
        val loggtekst = logg.joinToString("\n") { it.fullTekst() }
        loggtekst shouldContain "Ugyldig body i kall til skattepliktige"
        (svar.map { it.body() } + loggtekst).forEach { ELLEVE_SIFRE.find(it)?.value.shouldBeNull() }
    }

    @Test
    fun `400 for antall identer og inntektsår er JSON`() {
        listOf(
            forespørsel(2025, *(0..10).map { "2000000000$it".takeLast(11) to true }.toTypedArray()),
            forespørsel(2025),
            forespørsel(25, FNR to true),
        ).map { slåOpp(it, accept = "application/json") }.forEach { respons ->
            respons.statusCode() shouldBe 400
            respons.headers().firstValue("Content-Type").get() shouldContain "application/json"
            objectMapper.readTree(respons.body())["status"].asInt() shouldBe 400
            ELLEVE_SIFRE.find(respons.body())?.value.shouldBeNull()
        }
    }

    @Test
    fun `ingen databasetilkobling er i bruk mens Sigrun svarer`() {
        lagrePerson(FNR, periode(LocalDate.of(2025, 1, 1)))
        lagrePerson(DNR, periode(LocalDate.of(2025, 1, 1)))
        val pool = dataSource.unwrap(HikariDataSource::class.java).hikariPoolMXBean
        MålTilkoblinger.målinger.clear()
        MålTilkoblinger.måler = { pool.activeConnections }
        listOf(FNR, DNR).forEach {
            sigrun.stubFor(
                sigrunKall(it).willReturn(
                    WireMock.aResponse().withStatus(404).withFixedDelay(200).withTransformers(MålTilkoblinger.NAVN)
                )
            )
        }

        val respons = try {
            slåOpp(forespørsel(2025, FNR to true, DNR to false))
        } finally {
            MålTilkoblinger.måler = null
        }

        respons.statusCode() shouldBe 200
        MålTilkoblinger.målinger.toList() shouldBe listOf(0, 0)
    }

    /** Leser antall aktive databasetilkoblinger mens appen venter på svar fra Sigrun. */
    private object MålTilkoblinger : ResponseDefinitionTransformerV2 {
        const val NAVN = "mal-tilkoblinger"

        @Volatile
        var måler: (() -> Int)? = null
        val målinger = CopyOnWriteArrayList<Int>()

        override fun transform(serveEvent: ServeEvent): ResponseDefinition {
            måler?.let { målinger.add(it()) }
            return serveEvent.responseDefinition
        }

        override fun getName() = NAVN
        override fun applyGlobally() = false
    }

    @Test
    fun `11 identer gir 400, og det gjør ingen identer også`() {
        slåOpp(forespørsel(2025, *(0..10).map { "2000000000$it".takeLast(11) to true }.toTypedArray())).statusCode() shouldBe 400
        slåOpp(forespørsel(2025)).statusCode() shouldBe 400
    }

    @Test
    fun `ugyldig inntektsår gir 400`() {
        slåOpp(forespørsel(25, FNR to true)).statusCode() shouldBe 400
    }

    @Test
    fun `verken ident eller norskPersonidentifikator står i svaret eller i loggen`() {
        val personId = lagrePerson(FNR, periode(LocalDate.of(2025, 1, 1)))
        lagrePerson(DNR, periode(LocalDate.of(2024, 1, 1)))
        lagreInntekt(DNR, "2024", slettet = false)
        sigrunSvarer(FNR, 500)
        val elleveIdenter = forespørsel(2025, *(0..10).map { (FNR.toLong() + it).toString() to true }.toTypedArray())

        val (svar, logg) = LoggingTestUtils.withLogCapture { logg ->
            listOf(
                slåOpp(forespørsel(2025, FNR to true, DNR to false)).also { it.statusCode() shouldBe 200 },
                slåOpp(elleveIdenter).also { it.statusCode() shouldBe 400 },
                slåOpp(forespørsel(2025, FNR to true), token(azpName = "annen-klient")).also { it.statusCode() shouldBe 403 },
                slåOpp(UGYLDIG_GJELDENDE).also { it.statusCode() shouldBe 400 },
            ) to logg.toList()
        }

        val loggtekst = logg.joinToString("\n") { it.fullTekst() }
        loggtekst shouldContain "Personoppslag: klient=dev-fss:teammelosys:melosys, inntektsaar=2025, antallIdenter=2"
        loggtekst shouldContain "Oppslag i Sigrun feilet for person $personId"
        (svar.map { it.body() } + loggtekst).forEach { tekst ->
            ELLEVE_SIFRE.find(tekst)?.value.shouldBeNull()
            tekst shouldNotContain "norskPersonidentifikator"
            tekst shouldNotContain "\"ident\""
        }
    }

    @Test
    fun `endepunktet er skjult i API-dokumentasjonen`() {
        val dokumentasjon = httpClient.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/api-docs/admin/admin")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        dokumentasjon.statusCode() shouldBe 200
        dokumentasjon.body() shouldContain "/api/admin/skattepliktige"
        dokumentasjon.body() shouldNotContain "personoppslag"
    }

    private fun JsonNode.strenger() = values().map(JsonNode::asString)

    private fun ILoggingEvent.fullTekst(): String =
        listOfNotNull(formattedMessage, throwableProxy?.tekst()).joinToString("\n")

    private fun IThrowableProxy.tekst(): String =
        listOfNotNull("$className: $message", cause?.tekst()).joinToString("\n")

    private data class PeriodeMedPubliseringer(val fom: LocalDate, val publiseringer: List<Pair<String, LocalDateTime>>)

    private fun periode(fom: LocalDate, vararg publiseringer: Pair<String, LocalDateTime>) =
        PeriodeMedPubliseringer(fom, publiseringer.toList())

    private var sekvensnummer = 0L

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

    private fun lagreInntekt(ident: String, inntektsaar: String, slettet: Boolean) {
        val periode = personRepository.findPersonByIdent(ident)!!.perioder.first()
        pensjonsgivendeInntektRepository.save(
            PensjonsgivendeInntekt(
                periode = periode,
                historiskInntekt = PensjonsgivendeInntektResponse(
                    slettet = slettet, norskPersonidentifikator = ident, inntektsaar = inntektsaar
                ),
            )
        )
    }

    private fun sigrunKall(ident: String) =
        WireMock.post(WireMock.urlEqualTo("/api/v1/pensjonsgivendeinntektforfolketrygden"))
            .withRequestBody(WireMock.matchingJsonPath("$.personident", WireMock.equalTo(ident)))

    /** Identene appen spurte Sigrun om, sortert, ett element per kall. */
    private fun sigrunSpurtFor() =
        sigrun.findAll(WireMock.anyRequestedFor(WireMock.anyUrl()))
            .map { objectMapper.readTree(it.bodyAsString)["personident"].asString() }
            .sorted()

    private fun sigrunSvarer(ident: String, status: Int) {
        val svar = WireMock.aResponse().withStatus(status)
        if (status == 200) {
            svar.withHeader("Content-Type", "application/json").withBody(
                """{"norskPersonidentifikator": "$ident", "inntektsaar": "2025", "pensjonsgivendeInntekt": []}"""
            )
        }
        sigrun.stubFor(sigrunKall(ident).willReturn(svar))
    }

    private fun forespørsel(inntektsaar: Int, vararg identer: Pair<String, Boolean>) =
        """{"inntektsaar": $inntektsaar, "identer": [${identer.joinToString { (ident, gjeldende) -> """{"ident": "$ident", "gjeldende": $gjeldende}""" }}]}"""

    private fun token(azpName: String? = "dev-fss:teammelosys:melosys"): String =
        mockOAuth2Server.issueToken(
            "aad",
            "melosys",
            DefaultOAuth2TokenCallback(
                issuerId = "aad",
                subject = "melosys",
                audience = listOf("skattehendelser-test"),
                claims = azpName?.let { mapOf("azp_name" to it) } ?: mapOf("NAVident" to "Z999999"),
            )
        ).serialize()

    private fun slåOpp(body: String, token: String? = token(), accept: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/api/admin/skattepliktige/personoppslag"))
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .apply { accept?.let { header("Accept", it) } }
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        private const val FNR = "12345678901"
        private const val DNR = "41234567890"
        private const val UKJENT = "99999999999"
        private val ELLEVE_SIFRE = Regex("""\d{11}""")
        private const val SIGRUN_FORSINKELSE_MS = 5_000

        // Jackson tar med verdien i feilmeldingen når typen er feil
        private val UGYLDIG_GJELDENDE = """{"inntektsaar": 2025, "identer": [{"ident": "$FNR", "gjeldende": "$FNR"}]}"""

        private val sigrun = WireMockServer(
            WireMockConfiguration.wireMockConfig().dynamicPort().extensions(MålTilkoblinger)
        ).apply { start() }

        @JvmStatic
        @AfterAll
        fun stoppSigrun() {
            sigrun.stop()
        }
    }
}
