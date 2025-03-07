package no.nav.melosysskattehendelser.melosys.consumer

import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.stereotype.Component

@Component
class KafkaContainerMonitor(
    private val registry: KafkaListenerEndpointRegistry
) {
    fun isKafkaContainerStopped(): Boolean =
        registry.getListenerContainer(VedtakHendelseConsumer.LISTENER_ID)?.isRunning
            ?: throw IllegalStateException("Listener container not found for ID: ${VedtakHendelseConsumer.LISTENER_ID}")
}
