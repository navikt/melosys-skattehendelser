package no.nav.melosysskattehendelser.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class MeasuredMetricsAdapter(private val meterRegistry: MeterRegistry) : MeasuredMetricsProvider {

    override fun getMeasured(): List<MeasuredMetricsProvider.MethodTimingStatus> =
        meterRegistry.find("method.execution.time").meters().mapNotNull { meter ->
            val timer = meter as? Timer ?: return@mapNotNull null
            val method = meter.id.getTag("method") ?: return@mapNotNull null

            val count = timer.count()
            val totalTimeMs = timer.totalTime(TimeUnit.MILLISECONDS)

            MeasuredMetricsProvider.MethodTimingStatus(
                method = method,
                count = timer.count(),
                totalTimeMs = totalTimeMs,
                maxTimeMs = timer.max(TimeUnit.MILLISECONDS),
                avgTimeMs = if (count > 0) totalTimeMs / count else 0.0
            )
        }.sortedByDescending { it.maxTimeMs }
}
