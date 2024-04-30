package no.nav.melosysskattehendelser.melosys.producer

import no.nav.melosysskattehendelser.sigrun.Hendelse
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.kafka.core.KafkaTemplate

class SkattehendelserProducer(
    private val kafkaTemplate: KafkaTemplate<String, Hendelse>,
    private val topicName: String
) {


    fun publiserMelding(hendelse: Hendelse){
        val hendelseRecord = ProducerRecord<String, Hendelse>(topicName, hendelse)
        kafkaTemplate.send(hendelseRecord).get()
    }
}