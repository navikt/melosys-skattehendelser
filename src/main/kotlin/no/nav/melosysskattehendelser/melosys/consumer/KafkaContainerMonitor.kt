package no.nav.melosysskattehendelser.melosys.consumer

import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.stereotype.Component

@Component
class KafkaContainerMonitor(
    private val registry: KafkaListenerEndpointRegistry
) {
    fun isKafkaContainerStopped(): Boolean = !isKafkaContainerRunning()

    fun isKafkaContainerRunning(): Boolean = container().isRunning

    /** Gir true når lytteren ble startet, false når den alt kjørte. */
    fun startKafkaContainerHvisStoppet(): Boolean {
        val container = container()
        if (container.isRunning) return false
        container.start()
        return true
    }

    private fun container() =
        registry.getListenerContainer(VedtakHendelseConsumer.LISTENER_ID)
            ?: throw IllegalStateException("Listener container not found for ID: ${VedtakHendelseConsumer.LISTENER_ID}")
}
