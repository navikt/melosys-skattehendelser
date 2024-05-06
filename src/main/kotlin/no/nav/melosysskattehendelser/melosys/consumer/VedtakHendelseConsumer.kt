package no.nav.melosysskattehendelser.melosys.consumer

import no.nav.melosysskattehendelser.domain.PersonRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener

class VedtakHendelseConsumer(
    private val vedtakHendelseRepository: PersonRepository
) {

    @KafkaListener(
        id = "melosysVedtakMottatt",
        clientIdPrefix = "melosys-vedtak-mottatt",
        topics = ["\${melosys.kafka.consumer.topic}"],
        containerFactory = "melosysVedtakListenerContainerFactory",
        groupId = "\${melosys.kafka.consumer.groupId}"
    )
    fun vedtakHendelseConsumer(consumerRecord: ConsumerRecord<String, VedtakHendelseMelding>) {
        vedtakHendelseRepository.save(consumerRecord.value().toPerson())
    }
}