package no.nav.melosysskattehendelser.controller

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.melosysskattehendelser.prosessering.JobConfirmationService
import no.nav.melosysskattehendelser.prosessering.SkatteHendelsePubliseringOptions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDate

class AdminControllerTest {
    private val jobConfirmationService = mockk<JobConfirmationService>(relaxed = true)
    private val controller = AdminController(
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        jobConfirmationService
    )

    @Test
    fun `fraDato og fraSekvensnummer sammen gir 400 før bekreftelseskoden`() {
        every { jobConfirmationService.isValid(any()) } returns false


        val respons = controller.startHendelseProsessering(
            SkatteHendelsePubliseringOptions(fraDato = LocalDate.of(2025, 1, 1), fraSekvensnummer = 5)
        )


        respons.statusCode shouldBe HttpStatus.BAD_REQUEST
        respons.body shouldBe "fraDato og fraSekvensnummer kan ikke brukes sammen"
        verify(exactly = 0) { jobConfirmationService.generateToken() }
    }

    @Test
    fun `fraSekvensnummer 0 gir 400 før bekreftelseskoden`() {
        every { jobConfirmationService.isValid(any()) } returns false


        val respons = controller.startHendelseProsessering(SkatteHendelsePubliseringOptions(fraSekvensnummer = 0))


        respons.statusCode shouldBe HttpStatus.BAD_REQUEST
        respons.body shouldBe "fraSekvensnummer må være større enn 0"
        verify(exactly = 0) { jobConfirmationService.generateToken() }
    }
}
