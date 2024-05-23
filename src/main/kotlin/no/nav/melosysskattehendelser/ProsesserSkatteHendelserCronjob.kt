package no.nav.melosysskattehendelser

import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger { }

@Component
class ProsesserSkatteHendelserCronjob(private val skatteHendelsePublisering: SkatteHendelsePublisering) {

    @Scheduled(cron = "\${cron.job.prosesser-skatt-hendelser}")
    fun startHendelseProsessering() {
        log.info("Kjører cronjob for å hente og publisere skattehendelser") // (  )
        skatteHendelsePublisering.prosesserSkattHendelser()
    }
}