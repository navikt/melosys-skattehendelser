package no.nav.melosysskattehendelser.melosys.producer

import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.kafka.core.KafkaTemplate
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class SkattehendelserProducerKafka(
    private val kafkaTemplate: KafkaTemplate<String, MelosysSkatteHendelse>,
    private val topicName: String
) : SkattehendelserProducer {

    override fun publiserMelding(hendelse: MelosysSkatteHendelse) {
        val hendelseRecord = ProducerRecord<String, MelosysSkatteHendelse>(topicName, hendelse)

        try {
            kafkaTemplate.send(hendelseRecord)[15L, TimeUnit.SECONDS]
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt() // Restore interrupted state
            throw RuntimeException("Avbrutt ved sending av melding for hendelse: $hendelse", e)
        } catch (e: ExecutionException) {
            throw RuntimeException("Kunne ikke sende melding for hendelse: $hendelse", e.cause ?: e)
        } catch (e: TimeoutException) {
            throw RuntimeException("Timeout ved sending av melding for hendelse: $hendelse", e)
        }
    }
}