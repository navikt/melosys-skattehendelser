package no.nav.melosysskattehendelser

import no.nav.melosysskattehendelser.skatt.SkattHendelserFetcher
import org.springframework.stereotype.Component
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener

/***
 * Brukt for raskere testing av sigrun q2/ske, skal fjernes
 */
@Component
class StartupRunner(private val skattHendelserFetcher: SkattHendelserFetcher) : ApplicationListener<ApplicationReadyEvent> {
    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        val count = skattHendelserFetcher.hentHendelser(2000).count()
        println("Hentet $count hendelser fra startsekvensnummer 2000")
    }
}
