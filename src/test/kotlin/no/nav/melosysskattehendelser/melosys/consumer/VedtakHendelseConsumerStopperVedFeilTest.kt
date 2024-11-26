package no.nav.melosysskattehendelser.melosys.consumer

import ch.qos.logback.classic.Level
import io.kotest.matchers.shouldBe
import no.nav.melosysskattehendelser.LoggingTestUtils
import no.nav.melosysskattehendelser.PostgresTestContainerBase
import no.nav.melosysskattehendelser.melosys.KafkaOffsetChecker
import no.nav.melosysskattehendelser.melosys.KafkaTestProducer
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilNotNull
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
class VedtakHendelseConsumerStopperVedFeilTest(
    @Autowired private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${melosys.kafka.consumer.topic}") private val topic: String,
    @Autowired private val kafkaOffsetChecker: KafkaOffsetChecker
) : PostgresTestContainerBase() {
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
