package no.nav.melosysskattehendelser.melosys.consumer

import mu.KotlinLogging
import no.nav.melosysskattehendelser.domain.PersonRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener

private val log = KotlinLogging.logger { }

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
    fun vedtakHendelseConsumer(consumerRecord: ConsumerRecord<String, MelosysHendelse>) {
        val melding = consumerRecord.value().melding
        val vedtakHendelseMelding = melding as? VedtakHendelseMelding
            ?: return log.debug { "Ignorerer melding av type ${melding.javaClass.simpleName} " }

        log.info("Mottatt vedtakshendelse sakstype: ${vedtakHendelseMelding.sakstype} sakstema: ${vedtakHendelseMelding.sakstema}")

        vedtakHendelseRepository.findPersonByIdent(vedtakHendelseMelding.folkeregisterIdent)?.let {
            log.warn("person med ident(${vedtakHendelseMelding.folkeregisterIdent}) finnes allerede")
            return
        }

        vedtakHendelseRepository.save(vedtakHendelseMelding.toPerson())
    }
}