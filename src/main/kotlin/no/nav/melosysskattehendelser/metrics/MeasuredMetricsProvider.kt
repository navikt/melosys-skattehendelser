package no.nav.melosysskattehendelser.metrics

fun interface MeasuredMetricsProvider {
    fun getMeasured(): List<MethodTimingStatus>

    data class MethodTimingStatus(
        val method: String,
        val count: Long,
        val totalTimeMs: Double,
        val maxTimeMs: Double,
        val avgTimeMs: Double
    )
}