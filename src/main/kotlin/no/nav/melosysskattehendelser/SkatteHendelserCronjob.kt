package no.nav.melosysskattehendelser

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.Trigger
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Component
import java.util.concurrent.Executors

private val log = KotlinLogging.logger { }

@Component
class SkatteHendelserCronjob(
    private val skatteHendelsePublisering: SkatteHendelsePublisering,
    @Value("\${cron.job.prosesser-skatt-hendelser}") var cronExpression: String
) : SchedulingConfigurer {

    init {
        log.info("Cronjob for å hente og publisere skattehendelser er satt til $cronExpression")
    }

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.setScheduler(Executors.newScheduledThreadPool(1))
        taskRegistrar.addTriggerTask(
            { startHendelseProsessering() }, customTrigger()
        )
    }

    private fun customTrigger(): Trigger {
        return Trigger { triggerContext ->
            CronTrigger(cronExpression).nextExecution(triggerContext).apply {
                log.info("Next execution time of the cron job is set to $this")
            }
        }
    }

    fun startHendelseProsessering() {
        log.info("Kjører cronjob for å hente og publisere skattehendelser") // (  )
        skatteHendelsePublisering.prosesserSkattHendelser()
    }
}