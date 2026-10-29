package no.nav.melosysskattehendelser.melosys.producer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse
import io.kotest.matchers.collections.shouldHaveSize
import no.nav.melosysskattehendelser.LoggingTestUtils
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class SkattehendelserProducerKafkaTest {
    private val kafkaTemplate = mockk<KafkaTemplate<String, MelosysSkatteHendelse>>()
    private val producer = SkattehendelserProducerKafka(kafkaTemplate, "topic", dryRunPublisering = false)

    @Test
    fun `feilmelding ved feilet sending viser person-id, ikke fødselsnummer`() {
        every { kafkaTemplate.send(any<ProducerRecord<String, MelosysSkatteHendelse>>()) } returns
            CompletableFuture.failedFuture(IllegalStateException("broker nede"))


        val feil = shouldThrow<RuntimeException> {
            producer.publiserMelding(MelosysSkatteHendelse("2025", "12345678901", "ny"), 7)
        }


        feil.message!! shouldNotContain "12345678901"
        feil.message!! shouldContain "person 7"
    }

    @Test
    fun `feilmelding ved timeout viser ikke fødselsnummer`() {
        val future = mockk<CompletableFuture<SendResult<String, MelosysSkatteHendelse>>>()
        every { future.get(15L, TimeUnit.SECONDS) } throws TimeoutException()
        every { kafkaTemplate.send(any<ProducerRecord<String, MelosysSkatteHendelse>>()) } returns future


        val feil = shouldThrow<RuntimeException> {
            producer.publiserMelding(MelosysSkatteHendelse("2025", "12345678901", "ny"), 7)
        }


        feil.message!! shouldNotContain "12345678901"
    }

    @Test
    fun `dry run logger ikke fødselsnummer`() {
        val dryRunProducer = SkattehendelserProducerKafka(kafkaTemplate, "topic", dryRunPublisering = true)


        LoggingTestUtils.withLogCapture { logg ->
            dryRunProducer.publiserMelding(MelosysSkatteHendelse("2025", "12345678901", "ny"), 7)

            logg.map { it.formattedMessage }.filter { it.startsWith("Dry run") }.shouldHaveSize(1)
            logg.forEach { it.formattedMessage shouldNotContain "12345678901" }
        }
    }
}
