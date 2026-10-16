package no.nav.melosysskattehendelser.melosys.consumer

import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.melosysskattehendelser.controller.KafkaErrorController
import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.melosys.KafkaConfig
import no.nav.melosysskattehendelser.melosys.KafkaOffsetChecker
import no.nav.melosysskattehendelser.melosys.KafkaTestProducer
import no.nav.melosysskattehendelser.metrics.Metrikker
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.util.concurrent.TimeUnit

@ActiveProfiles("test")
@SpringBootTest
@EmbeddedKafka(count = 1, controlledShutdown = true, partitions = 1)
@ContextConfiguration(
    classes = [
        JacksonAutoConfiguration::class,
        KafkaAutoConfiguration::class,
        KafkaConfig::class,
        HoppOverKafkaFeilHandler::class,
        KafkaContainerMonitor::class,
        KafkaErrorController::class,
        KafkaTestProducer::class,
        KafkaOffsetChecker::class,
        VedtakHendelseConsumer::class,
        HoppOverKafkaFeilHandlerTest.TestConfig::class,
        Metrikker::class
    ]
)
class HoppOverKafkaFeilHandlerTest(
    @Autowired private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${melosys.kafka.consumer.topic}") private val topic: String,
    @Autowired private val kafkaOffsetChecker: KafkaOffsetChecker,
    @Autowired private val kafkaErrorController: KafkaErrorController,
    @Autowired private val personRepository: PersonRepository,
    @Autowired private val registry: KafkaListenerEndpointRegistry
) {

    @TestConfiguration
    class TestConfig {
        @Bean
        fun testPersonRepository() = mockk<PersonRepository>(relaxed = true)
    }

    @BeforeEach
    fun setUp() {
        clearMocks(personRepository)
        every { personRepository.findPersonByIdent(any()) } returns null
        every { personRepository.save(any<Person>()) } answers { firstArg() }
        every { personRepository.save(match<Person> { it.ident == FEILER }) } throws RuntimeException("databasen er nede for 1$FØDSELSNUMMER")
    }

    @AfterEach
    fun tearDown() {
        // Neste test forutsetter at lytteren går og at ingen feil ligger igjen
        lytteren().isRunning shouldBe true
        feil().shouldBeEmpty()
    }

    @Test
    fun `melding som kaster stopper lytteren og vises uten innhold, og DELETE hopper over den`() {
        kafkaOffsetChecker.offsetIncreased {
            kafkaTemplate.send(topic, vedtaksmelding(FEILER))
            val nøkkel = ventTilLytterenHarStoppet()

            val feiletMelding = feil().getValue(nøkkel)
            feiletMelding.feilkjede.joinToString().let {
                it shouldContain "databasen er nede for *"
                it shouldNotContain FØDSELSNUMMER
            }

            kafkaTemplate.send(topic, vedtaksmelding(NESTE))
            kafkaErrorController.hoppOver(nøkkel).statusCode shouldBe HttpStatus.OK

            verify(timeout = 10_000) { personRepository.save(match<Person> { it.ident == NESTE }) }
            await.timeout(10, TimeUnit.SECONDS).untilAsserted { lytteren().isRunning shouldBe true }
        }.shouldBe(2)
    }

    @Test
    fun `melding som ikke kan leses stopper lytteren, og DELETE hopper over den`() {
        kafkaOffsetChecker.offsetIncreased {
            kafkaTemplate.send(topic, """{ "melding": { "type": "VedtakHendelseMelding", "foo": "bar" } }""")
            val nøkkel = ventTilLytterenHarStoppet()

            feil().getValue(nøkkel).feilkjede.first() shouldContain "RecordDeserializationException"

            kafkaTemplate.send(topic, vedtaksmelding(NESTE))
            kafkaErrorController.hoppOver(nøkkel).statusCode shouldBe HttpStatus.OK

            verify(timeout = 10_000) { personRepository.save(match<Person> { it.ident == NESTE }) }
        }.shouldBe(2)
    }

    @Test
    fun `retry leser meldingen på nytt`() {
        kafkaOffsetChecker.offsetIncreased {
            kafkaTemplate.send(topic, vedtaksmelding(FEILER))
            val nøkkel = ventTilLytterenHarStoppet()

            // Feilen er rettet
            every { personRepository.save(match<Person> { it.ident == FEILER }) } answers { firstArg() }
            kafkaErrorController.prøvIgjen(nøkkel).statusCode shouldBe HttpStatus.OK

            verify(timeout = 10_000, exactly = 2) { personRepository.save(match<Person> { it.ident == FEILER }) }
            await.timeout(10, TimeUnit.SECONDS).untilAsserted { lytteren().isRunning shouldBe true }
        }.shouldBe(1)
    }

    @Test
    fun `DELETE og retry gir 409 mens lytteren kjører`() {
        kafkaErrorController.hoppOver("finnes-ikke-0-0").statusCode shouldBe HttpStatus.CONFLICT
        kafkaErrorController.prøvIgjen("finnes-ikke-0-0").statusCode shouldBe HttpStatus.CONFLICT
    }

    // Venter til lytteren har stoppet helt, så DELETE og retry ikke starter den mens den stopper
    private fun ventTilLytterenHarStoppet(): String {
        await.timeout(20, TimeUnit.SECONDS).untilAsserted {
            lytteren().isRunning shouldBe false
            lytteren().isChildRunning shouldBe false
            feil().size shouldBe 1
        }
        return feil().keys.single()
    }

    @Suppress("UNCHECKED_CAST")
    private fun feil() =
        kafkaErrorController.feil().body!!["feil"] as Map<String, HoppOverKafkaFeilHandler.FeiletMelding>

    private fun lytteren() = registry.getListenerContainer(VedtakHendelseConsumer.LISTENER_ID)!!

    private fun vedtaksmelding(ident: String) = """
        {
          "melding": {
            "type": "VedtakHendelseMelding",
            "folkeregisterIdent": "$ident",
            "sakstype": "FTRL",
            "sakstema": "TRYGDEAVGIFT",
            "medlemskapsperioder": [{ "fom": "2024-01-01", "tom": "2024-12-31", "innvilgelsesResultat": "INNVILGET" }]
          }
        }"""

    companion object {
        private const val FEILER = "feiler"
        private const val NESTE = "neste"
        private const val FØDSELSNUMMER = "12345678901"
    }
}
