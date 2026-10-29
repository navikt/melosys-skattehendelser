package no.nav.melosysskattehendelser.melosys

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.string.shouldNotContain
import no.nav.melosysskattehendelser.LoggingTestUtils
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.ProducerFactory
import java.util.concurrent.TimeUnit

class SkattehendelseKafkaTemplateTest {

    @Test
    fun `feilet sending logger ikke fødselsnummeret`() {
        val mockProducer = MockProducer(false, null, StringSerializer(), Serializer<MelosysSkatteHendelse> { _, _ -> ByteArray(0) })
        val producerFactory = ProducerFactory<String, MelosysSkatteHendelse> { mockProducer }
        val template = skattehendelseKafkaTemplate(producerFactory).apply { setDefaultTopic("topic") }


        LoggingTestUtils.withLogCapture { logg ->
            val future = template.sendDefault("7", MelosysSkatteHendelse("2025", "12345678901", "ny"))
            mockProducer.errorNext(RuntimeException("broker nede"))
            shouldThrowAny { future.get(5, TimeUnit.SECONDS) }

            logg.filter { it.loggerName.contains("LoggingProducerListener") }.shouldNotBeEmpty()
            logg.forEach { it.formattedMessage shouldNotContain "12345678901" }
        }
    }
}
