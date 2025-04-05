package no.nav.melosysskattehendelser.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class MeasuredMetricsAdapter(private val meterRegistry: MeterRegistry?) : MeasuredMetricsProvider {

    override fun getMeasured(): List<MeasuredMetricsProvider.MethodTimingStatus> {
        if (meterRegistry == null) return emptyList()

        return meterRegistry.find("method.execution.time").meters().mapNotNull { meter ->
            val timer = meter as? Timer ?: return@mapNotNull null
            val method = meter.id.getTag("method") ?: return@mapNotNull null

            MeasuredMetricsProvider.MethodTimingStatus(
                method = method,
                count = timer.count(),
                totalTimeMs = timer.totalTime(TimeUnit.MILLISECONDS),
                maxTimeMs = timer.max(TimeUnit.MILLISECONDS)
            )
        }.sortedByDescending { it.maxTimeMs }
    }
}