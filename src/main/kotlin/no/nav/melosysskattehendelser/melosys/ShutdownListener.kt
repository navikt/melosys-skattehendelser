package no.nav.melosysskattehendelser.melosys

import jakarta.annotation.PreDestroy
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextClosedEvent
import org.springframework.stereotype.Component

@Component
class ShutdownListener : ApplicationListener<ContextClosedEvent> {

    var time: Long = 0
    override fun onApplicationEvent(event: ContextClosedEvent) {
        println("Application context is closing")
        time = System.currentTimeMillis()
    }

    @PreDestroy
    fun stop() {
        println("Shutdown used ${System.currentTimeMillis() - time} ms since application was stopped")
    }

}
