package no.nav.melosysskattehendelser.melosys

import mu.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.concurrent.CountDownLatch

private val log = KotlinLogging.logger { }

@Component
class KafkaTestConsumer {

    var latch = CountDownLatch(1)
    var payload: String? = null

    @KafkaListener(topics = ["\${melosys.kafka.producer.topic}"])
    fun receive(consumerRecord: ConsumerRecord<*, *>) {
        log.info("received payload='{}'", consumerRecord.toString())
        payload = consumerRecord.value().toString()
        latch.countDown()
    }

    fun resetLatch() {
        latch = CountDownLatch(1)
    }
}