package no.nav.melosysskattehendelser

import org.springframework.stereotype.Component
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener

/***
 * Brukt for raskere testing av sigrun q2/ske, skal fjernes
 */
@Component
class StartupRunner(private val skatteHendelsePublisering: SkatteHendelsePublisering) : ApplicationListener<ApplicationReadyEvent> {
    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        skatteHendelsePublisering.prosesserHendelser()
    }
}
