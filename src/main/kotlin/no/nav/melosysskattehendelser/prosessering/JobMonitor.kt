package no.nav.melosysskattehendelser.prosessering

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.KotlinLogging
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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

    @Volatile
    var errorCount: Int = 0

    @Volatile
    var maxErrorsBeforeStop: Int = 0

    @Volatile
    var exceptions: MutableMap<String, Int> = mutableMapOf()

    private val methodToNanoTime = ConcurrentHashMap<String, AtomicLong>()
    private val methodToCount = ConcurrentHashMap<String, AtomicLong>()

    fun <T> sampleMethod(name: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            val duration = System.nanoTime() - start

            sampleMethod(name, duration)
        }
    }

    fun sampleMethod(name: String, duration: Long) {
        methodToCount.computeIfAbsent(name) { AtomicLong(0) }.incrementAndGet()
        methodToNanoTime.computeIfAbsent(name) { AtomicLong(0) }.addAndGet(duration)
    }

    fun sampleMethod(metodeTilNanoSec: Map<String, AtomicLong>) {
        metodeTilNanoSec.forEach {
            sampleMethod(it.key, it.value.get())
        }
    }

    fun getMethodStats(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()

        methodToNanoTime.keys.forEach { method ->
            val totalNanos = methodToNanoTime[method]?.get() ?: 0
            val count = methodToCount[method]?.get() ?: 0

            result[method] = mapOf(
                "totalTimeMs" to totalNanos / 1_000_000.0f,
                "count" to count,
                "avgTimeMs" to if (count > 0) (totalNanos / count) / 1_000_000.0f else 0.0f
            )
        }

        return result
    }

    fun execute(block: T.() -> Unit) {
        if (isRunning) {
            log.warn("Job '$jobName' is already running.")
            return
        }
        if (!canStart()) {
            log.warn("'$jobName' will not start. reason: $canNotStartMessage")
            return
        }
        isRunning = true
        startedAt = LocalDateTime.now()
        errorCount = 0
        exceptions.clear()
        stats.reset()
        return try {
            stats.block()
        } catch (ex: Exception) {
            log.error(ex) { "Job '$jobName' failed" }
            registerException(ex)
            throw ex
        } finally {
            isRunning = false
            shouldStop = false
            stoppedAt = LocalDateTime.now()
            log.info(
                "Job '$jobName' completed. Runtime: ${startedAt.durationUntil(stoppedAt)}" +
                        "\nStats: ${status().toJson()}"
            )
        }
    }

    fun registerException(e: Throwable) {
        val msg = e.message ?: e::class.simpleName ?: "Unknown error"
        exceptions[msg] = exceptions.getOrDefault(msg, 0) + 1
        if (errorCount++ >= maxErrorsBeforeStop) {
            stop()
            log.error { "Stopping processing due to too many ($maxErrorsBeforeStop) errors" }
        }
    }

    fun stop() {
        log.info("Stopping job '$jobName' stats: ${status().toJson()}")
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
            "metodeToMillis" to getMethodStats(),
        ) + stats.asMap() + mapOf(
            "errorCount" to errorCount,
            "exceptions" to exceptions
        )

    private fun Any.toJson() = jacksonObjectMapper()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .registerModule(JavaTimeModule())
        .writerWithDefaultPrettyPrinter()
        .writeValueAsString(this)


    interface Stats {
        fun reset()
        fun asMap(): Map<String, Any>
    }
}

inline fun <T> measure(
    statsMap: MutableMap<String, AtomicLong>,
    methodName: String,
    block: () -> T
): T {
    val start = System.nanoTime()
    try {
        return block()
    } finally {
        val duration = System.nanoTime() - start
        statsMap.computeIfAbsent(methodName) { AtomicLong(0) }.addAndGet(duration)
    }
}