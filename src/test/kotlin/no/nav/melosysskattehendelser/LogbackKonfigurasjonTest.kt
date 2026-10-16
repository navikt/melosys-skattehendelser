package no.nav.melosysskattehendelser

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.classic.util.LogbackMDCAdapter
import ch.qos.logback.core.OutputStreamAppender
import ch.qos.logback.core.status.Status
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class LogbackKonfigurasjonTest {

    private val fnr = "12345678901"

    @Test
    fun `lokalt logges til konsoll uten JSON`() {
        val context = konfigurer(naisClusterName = null)

        context.rotAppendere() shouldContainExactly listOf("console")
        context.advarsler().shouldBeEmpty()
    }

    @Test
    fun `i dev logges JSON uten maskering`() {
        val context = konfigurer(naisClusterName = "dev-gcp")

        context.rotAppendere() shouldContainExactly listOf("stdout_json")
        context.advarsler().shouldBeEmpty()
        context.formater("fnr $fnr") shouldContain "\"message\":\"fnr $fnr\""
    }

    @Test
    fun `i prod logges JSON med fødselsnummer maskert`() {
        val context = konfigurer(naisClusterName = "prod-gcp")

        context.rotAppendere() shouldContainExactly listOf("stdout_json")
        context.advarsler().shouldBeEmpty()
        context.formater("fnr $fnr").run {
            shouldContain("\"message\":\"fnr ***********\"")
            shouldNotContain(fnr)
        }
    }

    private fun konfigurer(naisClusterName: String?): LoggerContext =
        LoggerContext().apply {
            mdcAdapter = LogbackMDCAdapter()
            naisClusterName?.let { putProperty("NAIS_CLUSTER_NAME", it) }
            JoranConfigurator().also { it.context = this }
                .doConfigure(LogbackKonfigurasjonTest::class.java.getResource("/logback.xml"))
        }

    private fun LoggerContext.rotAppendere(): List<String> =
        getLogger(Logger.ROOT_LOGGER_NAME).iteratorForAppenders().asSequence().map { it.name }.toList()

    private fun LoggerContext.advarsler(): List<String> =
        statusManager.copyOfStatusList.filter { it.level >= Status.WARN }.map { it.message }

    private fun LoggerContext.formater(melding: String): String {
        val logger = getLogger("test")
        val appender = getLogger(Logger.ROOT_LOGGER_NAME).getAppender("stdout_json") as OutputStreamAppender<*>
        val event = LoggingEvent(Logger::class.java.name, logger, Level.INFO, melding, null, null)
        @Suppress("UNCHECKED_CAST")
        val encoder = appender.encoder as ch.qos.logback.core.encoder.Encoder<LoggingEvent>
        return String(encoder.encode(event))
    }
}
