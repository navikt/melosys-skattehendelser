package no.nav.melosysskattehendelser.melosys.producer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import java.util.concurrent.CompletableFuture

class SkattehendelserProducerKafkaTest {
    private val kafkaTemplate = mockk<KafkaTemplate<String, MelosysSkatteHendelse>>()
    private val producer = SkattehendelserProducerKafka(kafkaTemplate, "topic", dryRunPublisering = false)

    @Test
    fun `feilmelding ved feilet sending viser person-id, ikke fødselsnummer`() {
        every { kafkaTemplate.send(any<org.apache.kafka.clients.producer.ProducerRecord<String, MelosysSkatteHendelse>>()) } returns
            CompletableFuture.failedFuture(IllegalStateException("broker nede"))


        val feil = shouldThrow<RuntimeException> {
            producer.publiserMelding(MelosysSkatteHendelse("2025", "12345678901", "ny"), 7)
        }


        feil.message!! shouldNotContain "12345678901"
        feil.message!! shouldContain "person 7"
    }
}
