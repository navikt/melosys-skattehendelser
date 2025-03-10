package no.nav.melosysskattehendelser

import mu.KotlinLogging
import no.nav.melosysskattehendelser.prosessering.SkatteHendelsePublisering
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger { }

@Component
class ShutdownHandler(
    private val skatteHendelsePublisering: SkatteHendelsePublisering
) : SmartLifecycle {

    @Volatile
    private var isRunning = false

    override fun start() {
        isRunning = true
    }

    override fun stop() {
        skatteHendelsePublisering.stopProsesseringAvSkattHendelser()
    }

    override fun isRunning(): Boolean {
        return isRunning
    }

    override fun isAutoStartup(): Boolean {
        return true
    }

    override fun stop(callback: Runnable) {
        try {
            logger.info("Graceful shutdown initiated")
            stop()
        } finally {
            callback.run()
        }
    }

    override fun getPhase(): Int {
        return 0
    }
}
