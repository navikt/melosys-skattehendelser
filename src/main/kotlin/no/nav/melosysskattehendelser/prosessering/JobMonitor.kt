package no.nav.melosysskattehendelser.prosessering

import mu.KotlinLogging
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

private val log = KotlinLogging.logger {}

class JobMonitor<T : JobMonitor.Stats>(
    private val jobName: String,
    private val canStart: () -> Boolean = { true },
    private val canNotStartMessage: String = "",
    val stats: T
) {
    private val shouldStopAtomic = AtomicBoolean(false)

    var shouldStop: Boolean
        get() = shouldStopAtomic.get()
        set(value) = shouldStopAtomic.set(value)

    @Volatile
    private var isRunning: Boolean = false

    @Volatile
    private var startedAt: LocalDateTime = LocalDateTime.MIN

    fun execute(block: T.() -> Unit) {
        if (isRunning) {
            log.warn("Job '$jobName' is already running.")
            return
        }
        if (!canStart()) {
            log.warn("'$jobName' will not start. reason: $canNotStartMessage")
            return
        }

        stats.reset()
        return try {
            stats.block()
        } catch (ex: Exception) {
            log.error(ex) { "Job '$jobName' failed" }
            throw ex
        } finally {
            isRunning = false
            shouldStop = false
            log.info("Job '$jobName' completed.")
        }
    }

    fun stop() {
        log.info("Stopping job '$jobName' stats:${status()}")
        shouldStop = true
    }

    fun status(): Map<String, Any> =
        mapOf(
            "jobName" to jobName,
            "isRunning" to isRunning,
            "startedAt" to startedAt
        ) + stats.asMap()

    interface Stats {
        fun reset()
        fun asMap(): Map<String, Any>
    }
}
