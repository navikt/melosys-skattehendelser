package no.nav.melosysskattehendelser

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.awaitility.core.ConditionFactory

private val log = KotlinLogging.logger { }

object AwaitUtil {
    fun ConditionFactory.throwOnLogError(logEvents: List<ILoggingEvent>): ConditionFactory = this.conditionEvaluationListener {
        findWithErrors(logEvents)?.let {
            error("Fant log entry med level error: ${it.formattedMessage}")
        }
    }

    private fun findWithErrors(logEvents: List<ILoggingEvent>): ILoggingEvent? {
        for (i in 1..3) {
            try {
                return logEvents.firstOrNull { it.level == Level.ERROR }
            } catch (e: ConcurrentModificationException) {
                // Siden dette gjelder test er det raskere og prøve på nytt, en å synkronisere
                log.warn("ConcurrentModification during find last log message, retrying $i", e)
            }
        }
        return logEvents.firstOrNull { it.level == Level.ERROR }
    }
}
