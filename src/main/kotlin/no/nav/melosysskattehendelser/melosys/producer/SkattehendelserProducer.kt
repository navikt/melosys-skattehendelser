package no.nav.melosysskattehendelser.melosys.producer

import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.kafka.core.KafkaTemplate

class SkattehendelserProducer(
    private val kafkaTemplate: KafkaTemplate<String, MelosysSkatteHendelse>,
    private val topicName: String
) {


    fun publiserMelding(hendelse: MelosysSkatteHendelse){
        val hendelseRecord = ProducerRecord<String, MelosysSkatteHendelse>(topicName, hendelse)
        kafkaTemplate.send(hendelseRecord).get()
    }
}