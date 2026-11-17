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
import net.logstash.logback.argument.StructuredArguments
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

    @Test
    fun `i prod maskeres fødselsnummer som står inntil bokstav eller understrek`() {
        konfigurer(naisClusterName = "prod-gcp").formater("Ugyldig personident_$fnr, fnr$fnr").run {
            shouldContain("\"message\":\"Ugyldig personident_***********, fnr***********\"")
            shouldNotContain(fnr)
        }
    }

    @Test
    fun `i prod maskeres ikke sifferserier som ikke er 11 sifre`() {
        val melding = "Nav-Call-Id: CallId_1763380000000_123, sekvensnummer 1234567890, 123456789012"

        konfigurer(naisClusterName = "prod-gcp").formater(melding) shouldContain "\"message\":\"$melding\""
    }

    @Test
    fun `i prod maskeres ikke MDC-felt, også når de har 11 sifre på rad`() {
        val mdc = mapOf(
            "trace_id" to "4bf92f35a12345678901b4da6a3ce929",
            "span_id" to "a12345678901bcde",
            "trace_flags" to "01",
            "callId" to "12345678901-4a5b-8c9d-0e1f2a3b4c5d",
        )

        konfigurer(naisClusterName = "prod-gcp").formater("Ugyldig personident_$fnr", mdc = mdc).run {
            mdc.forEach { (felt, verdi) -> shouldContain("\"$felt\":\"$verdi\"") }
            shouldContain("\"message\":\"Ugyldig personident_***********\"")
        }
    }

    @Test
    fun `i prod maskeres ikke strukturerte felt, heller ikke et nøstet message`() {
        val argument = StructuredArguments.keyValue("otel", mapOf("message" to "a12345678901bcde"))

        konfigurer(naisClusterName = "prod-gcp").formater("Ugyldig personident_$fnr", argumenter = arrayOf(argument)).run {
            shouldContain("\"otel\":{\"message\":\"a12345678901bcde\"}")
            shouldContain("\"message\":\"Ugyldig personident_***********\"")
        }
    }

    @Test
    fun `i prod maskeres fødselsnummer i stack_trace`() {
        val feil = IllegalArgumentException("Ugyldig personident_$fnr")

        konfigurer(naisClusterName = "prod-gcp").formater("Feil", feil = feil).run {
            shouldContain("\"stack_trace\":\"java.lang.IllegalArgumentException: Ugyldig personident_***********")
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

    private fun LoggerContext.formater(
        melding: String,
        mdc: Map<String, String> = emptyMap(),
        feil: Throwable? = null,
        argumenter: Array<Any>? = null,
    ): String {
        val logger = getLogger("test")
        val appender = getLogger(Logger.ROOT_LOGGER_NAME).getAppender("stdout_json") as OutputStreamAppender<*>
        val event = LoggingEvent(Logger::class.java.name, logger, Level.INFO, melding, feil, argumenter)
        event.setMDCPropertyMap(mdc)
        @Suppress("UNCHECKED_CAST")
        val encoder = appender.encoder as ch.qos.logback.core.encoder.Encoder<LoggingEvent>
        return String(encoder.encode(event))
    }
}
