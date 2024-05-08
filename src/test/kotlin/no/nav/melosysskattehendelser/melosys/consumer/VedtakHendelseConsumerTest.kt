package no.nav.melosysskattehendelser.melosys.consumer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.melosysskattehendelser.PostgresTestContainerBase
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.melosys.KafkaTestProducer
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.util.concurrent.TimeUnit

@ActiveProfiles("test")
@SpringBootTest
@DirtiesContext
@EmbeddedKafka(count = 1, controlledShutdown = true, partitions = 1)
@Import(KafkaTestProducer::class)
@EnableMockOAuth2Server
class VedtakHendelseConsumerTest(
    @Autowired private val kafkaTemplate: KafkaTemplate<String, MelosysHendelse>,
    @Autowired private val personRepository: PersonRepository,
    @Value("\${melosys.kafka.consumer.topic}") private val topic: String
) : PostgresTestContainerBase() {

    @Test
    fun `skal lagre ned aktuelle personer`() {
        val vedtakHendelseMelding = MelosysHendelse(
            melding = VedtakHendelseMelding(
                "456789123",
                Sakstyper.FTRL,
                Sakstemaer.TRYGDEAVGIFT
            )
        )

        kafkaTemplate.send(topic, vedtakHendelseMelding)

        await.timeout(5, TimeUnit.SECONDS)
            .pollDelay(Duration.ofMillis(1000))
            .untilAsserted {
                personRepository.findAll().first()
                    .shouldNotBeNull()
                    .ident.shouldBe("456789123")
            }
    }
}
