package no.nav.melosysskattehendelser.prosessering

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.melosysskattehendelser.metrics.MeasuredMetricsProvider
import org.junit.jupiter.api.Test

class JobMonitorTest {
    private val jobMonitor = JobMonitor(
        jobName = "test",
        stats = object : JobMonitor.Stats {
            override fun reset() {}
            override fun asMap(): Map<String, Any> = emptyMap()
        },
        metricsProvider = mockk<MeasuredMetricsProvider>(relaxed = true)
    )

    @Test
    fun `stop mens ingen jobb går stopper ikke neste kjøring`() {
        jobMonitor.stop()
        var shouldStopVedStart: Boolean? = null


        jobMonitor.execute { shouldStopVedStart = jobMonitor.shouldStop }


        shouldStopVedStart shouldBe false
    }
}
