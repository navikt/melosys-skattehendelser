package no.nav.melosysskattehendelser.melosys.consumer

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest
@DirtiesContext
@EmbeddedKafka(count = 1, controlledShutdown = true, partitions = 1)
class VedtakHendelseConsumerStopperVedFeilTest(
    @Autowired private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${melosys.kafka.consumer.topic}") private val topic: String
) {
    @TestConfiguration
    class Config {
        @Bean
        fun testKafkaTemplate(
            kafkaProperties: KafkaProperties,
        ): KafkaTemplate<String, String> {
            val props =
                kafkaProperties.buildProducerProperties(null) +
                        mapOf(
                            "key.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
                            "value.serializer" to "org.apache.kafka.common.serialization.StringSerializer"
                        )
            return KafkaTemplate(DefaultKafkaProducerFactory(props))
        }
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

        kafkaTemplate.send(topic, meldingMedFeil)


        Thread.sleep(2000)

    }
}
