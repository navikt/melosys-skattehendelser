package no.nav.melosysskattehendelser.controller

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.melosysskattehendelser.melosys.consumer.HoppOverKafkaFeilHandler
import no.nav.melosysskattehendelser.melosys.consumer.KafkaContainerMonitor
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class KafkaErrorControllerTest {
    private val feilHandler = mockk<HoppOverKafkaFeilHandler>(relaxed = true)
    private val kafkaContainerMonitor = mockk<KafkaContainerMonitor>(relaxed = true)
    private val controller = KafkaErrorController(feilHandler, kafkaContainerMonitor, "pod")

    // Feilen vises før lytteren har stoppet. Fjernes den da, står lytteren stoppet uten noe å hoppe over.
    @Test
    fun `DELETE og retry rører ikke feilen før lytteren har stoppet`() {
        every { feilHandler.merkForÅHoppeOver(any()) } returns true
        every { feilHandler.fjern(any()) } returns true
        every { kafkaContainerMonitor.isKafkaContainerRunning() } returns true

        controller.hoppOver("topic-0-1").statusCode shouldBe HttpStatus.CONFLICT
        controller.prøvIgjen("topic-0-1").statusCode shouldBe HttpStatus.CONFLICT

        verify(exactly = 0) { feilHandler.merkForÅHoppeOver(any()) }
        verify(exactly = 0) { feilHandler.fjern(any()) }
    }

    @Test
    fun `DELETE gir 409 mens den gamle consumeren fortsatt stopper`() {
        every { kafkaContainerMonitor.isKafkaContainerRunning() } returns false
        every { kafkaContainerMonitor.isKafkaContainerChildRunning() } returns true

        controller.hoppOver("topic-0-1").statusCode shouldBe HttpStatus.CONFLICT
        verify(exactly = 0) { feilHandler.merkForÅHoppeOver(any()) }
    }

    @Test
    fun `ukjent nøkkel gir 404 når lytteren har stoppet`() {
        every { feilHandler.merkForÅHoppeOver(any()) } returns false
        every { feilHandler.fjern(any()) } returns false
        every { kafkaContainerMonitor.isKafkaContainerRunning() } returns false

        controller.hoppOver("topic-0-1").statusCode shouldBe HttpStatus.NOT_FOUND
        controller.prøvIgjen("topic-0-1").statusCode shouldBe HttpStatus.NOT_FOUND

        verify(exactly = 0) { kafkaContainerMonitor.startKafkaContainerHvisStoppet() }
    }
}
