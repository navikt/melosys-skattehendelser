package no.nav.melosysskattehendelser.prosessering

import mu.KotlinLogging
import java.time.Duration
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
    private var startedAt: LocalDateTime? = null

    @Volatile
    private var stoppedAt: LocalDateTime? = null

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
            log.info("Job '$jobName' completed. Runtime: ${startedAt.durationUntil(stoppedAt)}")
        }
    }

    fun stop() {
        log.info("Stopping job '$jobName' stats:${status()}")
        shouldStop = true
    }

    private fun LocalDateTime?.durationUntil(other: LocalDateTime?): String =
        Duration.between(this ?: LocalDateTime.now(), other ?: LocalDateTime.now()).format()

    private fun Duration.format(): String =
        if (toMillis() < 1000) "${toMillis()} ms" else String.format("%.2f sec", toMillis() / 1000.0)

    fun status(): Map<String, Any?> =
        mapOf(
            "jobName" to jobName,
            "isRunning" to isRunning,
            "startedAt" to startedAt,
            "runtime" to startedAt.durationUntil(stoppedAt),
        ) + stats.asMap()

    interface Stats {
        fun reset()
        fun asMap(): Map<String, Any>
    }
}
