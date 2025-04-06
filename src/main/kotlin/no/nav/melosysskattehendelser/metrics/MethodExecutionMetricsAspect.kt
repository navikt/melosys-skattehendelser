package no.nav.melosysskattehendelser.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

@Aspect
@Component
class MethodExecutionMetricsAspect(private val meterRegistry: MeterRegistry) {

    @Around("@annotation(Measured)")
    fun recordExecutionTime(joinPoint: ProceedingJoinPoint): Any? {
        val methodName = joinPoint.signature.toShortString()
        val timer = Timer.builder("method.execution.time")
            .tag("method", methodName)
            .register(meterRegistry)

        return timer.recordCallable { joinPoint.proceed() }
    }
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Measured
