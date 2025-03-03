package no.nav.melosysskattehendelser.melosys.consumer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.melosysskattehendelser.AwaitUtil.throwOnLogError
import no.nav.melosysskattehendelser.LoggingTestUtils
import no.nav.melosysskattehendelser.PostgresTestContainerBase
import no.nav.melosysskattehendelser.domain.Periode
import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.melosys.KafkaOffsetChecker
import no.nav.melosysskattehendelser.melosys.KafkaTestProducer
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@ActiveProfiles("test")
@SpringBootTest
@DirtiesContext
@EmbeddedKafka(count = 1, controlledShutdown = true, partitions = 1)
@Import(KafkaTestProducer::class, KafkaOffsetChecker::class, PersonTestService::class)
class VedtakHendelseConsumerTest(
    @Autowired private val kafkaTemplate: KafkaTemplate<String, String>,
    @Autowired private val personRepository: PersonRepository,
    @Value("\${melosys.kafka.consumer.topic}") private val topic: String,
    @Autowired private val kafkaOffsetChecker: KafkaOffsetChecker,
    @Autowired private val personTestService: PersonTestService,
    @Autowired private val objectMapper: ObjectMapper
) : PostgresTestContainerBase() {

    private val ident = "456789123"

    @AfterEach
    fun tearDown() {
        personRepository.findPersonByIdent(ident)?.let {
            personRepository.delete(it)
        }
    }

    @Test
    fun `skal lagre ikke lagre ned persone om vi ikke har periode`() {
        val vedtakHendelseMelding = """
            {
              "melding" : {
                "type" : "VedtakHendelseMelding",
                "folkeregisterIdent" : "$ident",
                "sakstype" : "FTRL",
                "sakstema" : "TRYGDEAVGIFT",
                "medlemskapsperioder": []
              }
            }
        """

        kafkaOffsetChecker.offsetIncreased {
            LoggingTestUtils.withLogCapture { logItems ->

                kafkaTemplate.send(topic, vedtakHendelseMelding)

                await.timeout(5, TimeUnit.SECONDS)
                    .untilAsserted {
                        logItems.firstOrNull {
                            it.formattedMessage.contains("Ingen medlemskapsperioder i melding, så lager ikke bruker i databasen")
                        }.shouldNotBeNull()
                    }
            }
        }.shouldBe(1)

        personRepository.findPersonByIdent(ident) shouldBe null
    }

    @Test
    fun `skal lagre ned aktuelle personer med perioder`() {
        val vedtakHendelseMelding = """
            {
              "melding" : {
                "type" : "VedtakHendelseMelding",
                "folkeregisterIdent" : "$ident",
                "sakstype" : "FTRL",
                "sakstema" : "TRYGDEAVGIFT",
                "medlemskapsperioder": [{
                      "fom": [2021, 1, 1],
                      "tom": [2022, 1, 1],
                      "innvilgelsesResultat": "INNVILGET"
                }]
              }
            }
        """

        kafkaOffsetChecker.offsetIncreased {
            kafkaTemplate.send(topic, vedtakHendelseMelding)

            await.timeout(5, TimeUnit.SECONDS)
                .untilAsserted {
                    personRepository.findPersonByIdent(ident)
                        .shouldNotBeNull()
                        .perioder.shouldHaveSize(1)
                }
        }.shouldBe(1)
    }

    @Test
    fun `skal legge til periode om person finnes`() {
        personTestService.savePerson(Person(ident = ident))

        val vedtakHendelseMelding = """
            {
              "melding" : {
                "type" : "VedtakHendelseMelding",
                "folkeregisterIdent" : "$ident",
                "sakstype" : "FTRL",
                "sakstema" : "TRYGDEAVGIFT",
                "medlemskapsperioder": [{
                      "fom": [2021, 1, 1],
                      "tom": [2022, 1, 1],
                      "innvilgelsesResultat": "INNVILGET"
                }]
              }
            }
        """

        kafkaOffsetChecker.offsetIncreased {
            kafkaTemplate.send(topic, vedtakHendelseMelding)

            await.timeout(5, TimeUnit.SECONDS)
                .untilAsserted {
                    personRepository.findPersonByIdent(ident)
                        .shouldNotBeNull()
                        .perioder.shouldHaveSize(1)
                }

        }.shouldBe(1)
    }

    @Test
    fun `skal ikke legge til duplikat periode`() {
        personTestService.savePerson(
            Person(ident = ident).apply {
                perioder.add(
                    Periode(
                        id = 0,
                        this,
                        LocalDate.of(2021, 1, 1),
                        LocalDate.of(2022, 1, 1)
                    )
                )
            }
        )

        val vedtakHendelseMelding = """
            {
              "melding" : {
                "type" : "VedtakHendelseMelding",
                "folkeregisterIdent" : "$ident",
                "sakstype" : "FTRL",
                "sakstema" : "TRYGDEAVGIFT",
                "medlemskapsperioder": [{
                      "fom": [2021, 1, 1],
                      "tom": [2022, 1, 1],
                      "innvilgelsesResultat": "INNVILGET"
                }]
              }
            }
        """

        kafkaOffsetChecker.offsetIncreased {
            LoggingTestUtils.withLogCapture { logItems ->
                kafkaTemplate.send(topic, vedtakHendelseMelding)

                await.timeout(5, TimeUnit.SECONDS)
                    .untilAsserted {
                        logItems.firstOrNull() { it.formattedMessage.contains("finnes allerede på person med id:") }
                            .shouldNotBeNull()
                    }

                personRepository.findPersonByIdent(ident)
                    .shouldNotBeNull()
                    .perioder.shouldHaveSize(1)
            }

        }.shouldBe(1)
    }

    @Test
    fun `skal ignorere ukjent melding uten å feile`() {
        val vedtakHendelseMelding = """
            {
              "melding" : {
                "type" : "UkjentType"
              }
            }
        """

        kafkaOffsetChecker.offsetIncreased {
            LoggingTestUtils.withLogCapture { logItems ->
                kafkaTemplate.send(topic, vedtakHendelseMelding)

                await.throwOnLogError(logItems)
                    .timeout(5, TimeUnit.SECONDS)
                    .untilAsserted {
                        logItems.firstOrNull() { it.formattedMessage.contains("Ignorerer melding av type UkjentMelding") }
                            .shouldNotBeNull()
                    }
            }
        }.shouldBe(1)
    }

    @Test
    fun `Ignorer medlemskapsperiode med null for fom eller tom`() {

        val vedtakHendelseMelding = vedtakHendelseMelding(
            listOf(
                Periode(
                    fom = null,
                    tom = null,
                    innvilgelsesResultat = InnvilgelsesResultat.INNVILGET
                )
            )
        )

        kafkaOffsetChecker.offsetIncreased {
            kafkaTemplate.send(topic, vedtakHendelseMelding)

            await.timeout(5, TimeUnit.SECONDS)
                .untilAsserted {
                    personRepository.findPersonByIdent(ident)
                        .shouldNotBeNull()
                        .perioder.shouldBeEmpty()
                }
        }.shouldBe(1)
    }

    @Test
    fun `Ignorer medlemskapsperiode med null for fom eller tom når vi alt har bruker i db`() {
        personTestService.savePerson(Person(ident = ident))

        val vedtakHendelseMelding = vedtakHendelseMelding(
            listOf(
                Periode(
                    fom = null,
                    tom = null,
                    innvilgelsesResultat = InnvilgelsesResultat.INNVILGET
                )
            )
        )

        kafkaOffsetChecker.offsetIncreased {
            kafkaTemplate.send(topic, vedtakHendelseMelding)

            await.timeout(5, TimeUnit.SECONDS)
                .untilAsserted {
                    personRepository.findPersonByIdent(ident)
                        .shouldNotBeNull()
                        .perioder.shouldBeEmpty()
                }
        }.shouldBe(1)
    }

    @Test
    fun `Ignorer medlemskapsperiode som ikke er INNVILGET`() {

        val vedtakHendelseMelding = vedtakHendelseMelding(
            InnvilgelsesResultat.entries.mapIndexed { index, melding ->
                Periode(
                    fom = LocalDate.of(2021, 1, 1),
                    tom = LocalDate.of(2023, 1, index + 1),
                    innvilgelsesResultat = melding
                )
            }
        )

        kafkaOffsetChecker.offsetIncreased {
            kafkaTemplate.send(topic, vedtakHendelseMelding)

            await.timeout(5, TimeUnit.SECONDS)
                .untilAsserted {
                    personRepository.findPersonByIdent(ident)
                        .shouldNotBeNull()
                        .perioder.shouldHaveSize(1)
                        .single().tom shouldBe LocalDate.of(2023, 1, 1)
                }
        }.shouldBe(1)
    }

    @Test
    fun `det må fungere å hente opp person med medlemskap perioder og så legge på flere`() {

        kafkaOffsetChecker.offsetIncreased {
            kafkaTemplate.send(
                topic, vedtakHendelseMelding(
                    listOf(
                        Periode(
                            fom = LocalDate.of(2020, 5, 1),
                            tom = LocalDate.of(2020, 6, 1),
                            innvilgelsesResultat = InnvilgelsesResultat.INNVILGET
                        )
                    )
                )
            )

            kafkaTemplate.send(
                topic, vedtakHendelseMelding(
                    listOf(
                        Periode(
                            fom = LocalDate.of(2022, 1, 1),
                            tom = LocalDate.of(2022, 2, 26),
                            innvilgelsesResultat = InnvilgelsesResultat.INNVILGET
                        ),
                        Periode(
                            fom = LocalDate.of(2025, 2, 27),
                            tom = LocalDate.of(2025, 5, 30),
                            innvilgelsesResultat = InnvilgelsesResultat.INNVILGET
                        )
                    )
                )
            )

            await.timeout(5, TimeUnit.SECONDS)
                .untilAsserted {
                    personRepository.findPersonByIdent(ident).shouldNotBeNull()
                        .perioder
                        .shouldHaveSize(3)
                }
        }.shouldBe(2)
    }

    private fun vedtakHendelseMelding(
        perioder: List<no.nav.melosysskattehendelser.melosys.consumer.Periode>
    ) = """
    {
        "melding": {
            "type": "VedtakHendelseMelding",
            "folkeregisterIdent": "$ident",
            "sakstype": "FTRL",
            "sakstema": "MEDLEMSKAP_LOVVALG",
            "behandligsresultatType": "MEDLEM_I_FOLKETRYGDEN",
            "vedtakstype": "FØRSTEGANGSVEDTAK",
            "medlemskapsperioder": ${perioder.toJson()},
            "lovvalgsperioder": []
        }
    }
        """

    private fun Any.toJson(): String = objectMapper.valueToTree<JsonNode>(this).toPrettyString()
}
