package no.nav.melosysskattehendelser

import mu.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger { }

@Component
class SkatteHendelserCronjob(
    private val skatteHendelsePublisering: SkatteHendelsePublisering
) {
    @Scheduled(cron = "\${cron.job.prosesser-skatt-hendelser}")
    @SchedulerLock(name = "hent skattehendelser", lockAtMostFor = "PT15M")
    fun startHendelseProsessering() {
        log.info("Kjører cronjob for å hente og publisere skattehendelser")
        skatteHendelsePublisering.prosesserSkattHendelser()
    }
}