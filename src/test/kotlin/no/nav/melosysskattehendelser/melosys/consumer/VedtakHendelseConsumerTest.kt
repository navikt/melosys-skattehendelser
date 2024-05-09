package no.nav.melosysskattehendelser.melosys.consumer

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.melosysskattehendelser.AwaitUtil.throwOnLogError
import no.nav.melosysskattehendelser.LoggingTestUtils
import no.nav.melosysskattehendelser.PostgresTestContainerBase
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.melosys.KafkaTestProducer
import no.nav.melosysskattehendelser.melosys.KafkaOffsetChecker
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
import java.util.concurrent.TimeUnit

@ActiveProfiles("test")
@SpringBootTest
@DirtiesContext
@EmbeddedKafka(count = 1, controlledShutdown = true, partitions = 1)
@Import(KafkaTestProducer::class, KafkaOffsetChecker::class)
class VedtakHendelseConsumerTest(
    @Autowired private val kafkaTemplate: KafkaTemplate<String, String>,
    @Autowired private val personRepository: PersonRepository,
    @Value("\${melosys.kafka.consumer.topic}") private val topic: String,
    @Autowired private val kafkaOffsetChecker: KafkaOffsetChecker
) : PostgresTestContainerBase() {

    private val ident = "456789123"

    @AfterEach
    fun tearDown() {
        personRepository.findAll().filter { it.ident == ident }
            .forEach { personRepository.delete(it) }
    }

    @Test
    fun `skal lagre ned aktuelle personer`() {
        val vedtakHendelseMelding = """
            {
              "melding" : {
                "type" : "VedtakHendelseMelding",
                "folkeregisterIdent" : "$ident",
                "sakstype" : "FTRL",
                "sakstema" : "TRYGDEAVGIFT"
              }
            }
        """

        kafkaOffsetChecker.offsetIncreased {
            kafkaTemplate.send(topic, vedtakHendelseMelding)

            await.timeout(5, TimeUnit.SECONDS)
                .untilAsserted {
                    personRepository.findAll().firstOrNull { it.ident == ident }
                        .shouldNotBeNull()
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
}
