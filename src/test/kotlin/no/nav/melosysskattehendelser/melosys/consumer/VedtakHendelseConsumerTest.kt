package no.nav.melosysskattehendelser.melosys.consumer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.nulls.shouldNotBeNull
import no.nav.melosysskattehendelser.PostgresTestContainerBase
import no.nav.melosysskattehendelser.domain.PersonRepository
import org.apache.kafka.common.serialization.StringSerializer
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.util.concurrent.TimeUnit

@ActiveProfiles("test")
@SpringBootTest
@DirtiesContext
@EmbeddedKafka(count = 1, controlledShutdown = true, partitions = 1)
class VedtakHendelseConsumerTest(
    @Autowired private val kafkaTemplate: KafkaTemplate<String, MelosysHendelse>,
    @Autowired private val personRepository: PersonRepository,
    @Value("\${melosys.kafka.consumer.topic}") private val topic: String
) : PostgresTestContainerBase() {

    private val ident = "456789123"

    @TestConfiguration
    class KafkaTestProducer {
        @Bean
        fun testKafkaTemplate(
            kafkaProperties: KafkaProperties,
            objectMapper: ObjectMapper?
        ): KafkaTemplate<String, MelosysHendelse> = KafkaTemplate(
            DefaultKafkaProducerFactory(
                kafkaProperties.buildProducerProperties(null),
                StringSerializer(),
                JsonSerializer(objectMapper)
            )
        )
    }

    @AfterEach
    fun tearDown() {
        personRepository.findAll().filter { it.ident == ident }
            .forEach { personRepository.delete(it) }
    }

    @Test
    fun `skal lagre ned aktuelle personer`() {
        val vedtakHendelseMelding = MelosysHendelse(
            melding = VedtakHendelseMelding(
                ident,
                Sakstyper.FTRL,
                Sakstemaer.TRYGDEAVGIFT
            )
        )

        kafkaTemplate.send(topic, vedtakHendelseMelding)

        await.timeout(5, TimeUnit.SECONDS)
            .pollDelay(Duration.ofMillis(1000))
            .untilAsserted {
                personRepository.findAll().firstOrNull { it.ident == ident }
                    .shouldNotBeNull()
            }
    }

    private fun Any.toJson(): String = jacksonObjectMapper().valueToTree<JsonNode?>(this).toPrettyString()
}
