package no.nav.melosysskattehendelser.melosys.producer

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import no.nav.melosysskattehendelser.PostgresTestContainerBase
import no.nav.melosysskattehendelser.melosys.KafkaTestConsumer
import no.nav.melosysskattehendelser.skatt.Hendelse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.TimeUnit


@ActiveProfiles("test")
@SpringBootTest
@DirtiesContext
@EmbeddedKafka(
    count = 1, controlledShutdown = true, partitions = 1,
    topics = ["teammelosys.skattehendelser.v1"],
)
@Import(KafkaTestConsumer::class)
class SkattehendelserProducerTest(
    @Autowired private val kafkaTestConsumer: KafkaTestConsumer,
    @Autowired private val skattehendelserProducer: SkattehendelserProducer
) : PostgresTestContainerBase() {

    @Test
    fun `skal publisere meldinger med skattehendelser`() {
        val hendelse = Hendelse(
            "2023",
            "123456",
            56,
            true
        )
        skattehendelserProducer.publiserMelding(hendelse)
        kafkaTestConsumer.latch.await(10, TimeUnit.SECONDS)
            .shouldBeTrue()
        kafkaTestConsumer.payload
            .shouldNotBeNull()
            .shouldEqualJson(
                """
                    {
                      "gjelderPeriode":"2023",
                      "identifikator":"123456",
                      "sekvensnummer":56,
                      "somAktoerid":true
                    }
                """
            )

    }
}
