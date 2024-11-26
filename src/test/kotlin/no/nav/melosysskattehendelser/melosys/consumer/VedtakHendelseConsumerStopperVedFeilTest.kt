package no.nav.melosysskattehendelser.melosys.consumer

import ch.qos.logback.classic.Level
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.melosysskattehendelser.LoggingTestUtils
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.melosys.KafkaConfig
import no.nav.melosysskattehendelser.melosys.KafkaOffsetChecker
import no.nav.melosysskattehendelser.melosys.KafkaTestProducer
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
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
        KafkaTestProducer::class,
        KafkaOffsetChecker::class,
        VedtakHendelseConsumer::class,
        VedtakHendelseConsumerStopperVedFeilTest.TestConfig::class
    ]
)

class VedtakHendelseConsumerStopperVedFeilTest(
    @Autowired private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${melosys.kafka.consumer.topic}") private val topic: String,
    @Autowired private val kafkaOffsetChecker: KafkaOffsetChecker
) {

    @TestConfiguration
    class TestConfig {
        @Bean
        fun testPersonRepository() = mockk<PersonRepository>(relaxed = true)
    }

    @Test
    fun `skal feile ved ugyldlig json`() {
        val meldingMedFeil = """
            {
                "melding": {
                    "type": "VedtakHendelseMelding",
                    "foo" : "bar"
                }
            }"""


        kafkaOffsetChecker.offsetIncreased {
            LoggingTestUtils.withLogCapture { logItems ->
                kafkaTemplate.send(topic, meldingMedFeil)

                val errorLogItem = await
                    .timeout(5, TimeUnit.SECONDS)
                    .untilNotNull {
                        logItems.firstOrNull() { it.level == Level.ERROR }
                    }
                errorLogItem.message shouldBe "Failed to deserialize message on topic teammelosys.melosys-hendelser-test: $meldingMedFeil"
            }
        }.shouldBe(0)
    }
}
