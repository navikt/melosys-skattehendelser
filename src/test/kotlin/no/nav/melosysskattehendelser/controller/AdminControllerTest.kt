package no.nav.melosysskattehendelser.controller

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import no.nav.melosysskattehendelser.LoggingTestUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.melosysskattehendelser.domain.Periode
import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse
import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducerFake
import no.nav.melosysskattehendelser.prosessering.JobConfirmationService
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntekt
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektConsumer
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektRequest
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektResponse
import no.nav.melosysskattehendelser.prosessering.SkatteHendelsePubliseringOptions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDate

class AdminControllerTest {
    private val jobConfirmationService = mockk<JobConfirmationService>(relaxed = true)
    private val personRepository = mockk<PersonRepository>()
    private val skattehendelserProducer = SkattehendelserProducerFake()
    private val pensjonsgivendeInntektConsumer = mockk<PensjonsgivendeInntektConsumer>()
    private val controller = AdminController(
        mockk(relaxed = true),
        personRepository,
        mockk(relaxed = true),
        mockk(relaxed = true),
        skattehendelserProducer,
        jobConfirmationService,
        pensjonsgivendeInntektConsumer,
    )

    private fun personMedPeriode2025() = Person(id = 7, ident = "12345678901").apply {
        perioder.add(Periode(person = this, fom = LocalDate.of(2025, 1, 1), tom = LocalDate.of(2025, 12, 31)))
    }

    @Test
    fun `publiserer melding for person med person-id som nøkkel`() {
        every { personRepository.findPersonById(7) } returns personMedPeriode2025()


        val respons = controller.lagKafkaMeldingForPerson(7, "2025", "ny")


        respons.statusCode shouldBe HttpStatus.OK
        respons.body shouldBe "Melding publisert for person 7, gjelderPeriode 2025"
        skattehendelserProducer.hendelser.single() shouldBe MelosysSkatteHendelse("2025", "12345678901", "ny")
        skattehendelserProducer.personIder.single() shouldBe 7L
    }

    @Test
    fun `publisering uten person logger ikke fødselsnummer`() {
        LoggingTestUtils.withLogCapture { logg ->
            controller.lagKafkaMelding(MelosysSkatteHendelse("2025", "12345678901", "ny"))

            logg.forEach { it.formattedMessage shouldNotContain "12345678901" }
        }
        skattehendelserProducer.hendelser.single().identifikator shouldBe "12345678901"
    }

    @Test
    fun `oppslag i Sigrun gir ordningene uten fødselsnummer`() {
        every { personRepository.findPersonById(7) } returns personMedPeriode2025()
        val ordning = PensjonsgivendeInntekt("FASTLAND", "2026-08-14", "500000", null, null, null)
        every {
            pensjonsgivendeInntektConsumer.hentPensjonsgivendeInntekt(PensjonsgivendeInntektRequest("2025", "12345678901"))
        } returns PensjonsgivendeInntektResponse(
            norskPersonidentifikator = "12345678901", inntektsaar = "2025", pensjonsgivendeInntekt = listOf(ordning)
        )


        val respons = controller.hentPensjonsgivendeInntekt(7, "2025")


        respons.statusCode shouldBe HttpStatus.OK
        respons.body shouldBe PensjonsgivendeInntektOppslag(7, "2025", funnet = true, ordninger = listOf(ordning))
        respons.body.toString() shouldNotContain "12345678901"
    }

    @Test
    fun `404 fra Sigrun gir funnet false`() {
        every { personRepository.findPersonById(7) } returns personMedPeriode2025()
        every { pensjonsgivendeInntektConsumer.hentPensjonsgivendeInntekt(any()) } returns PensjonsgivendeInntektResponse(
            slettet = true, norskPersonidentifikator = "12345678901", inntektsaar = "2025"
        )


        val respons = controller.hentPensjonsgivendeInntekt(7, "2025")


        respons.body shouldBe PensjonsgivendeInntektOppslag(7, "2025", funnet = false, ordninger = emptyList())
    }

    @Test
    fun `oppslag for ukjent person gir 404 uten kall til Sigrun`() {
        every { personRepository.findPersonById(7) } returns null


        val respons = controller.hentPensjonsgivendeInntekt(7, "2025")


        respons.statusCode shouldBe HttpStatus.NOT_FOUND
        verify(exactly = 0) { pensjonsgivendeInntektConsumer.hentPensjonsgivendeInntekt(any()) }
    }

    @Test
    fun `oppslag med ugyldig inntektsår gir 400`() {
        val respons = controller.hentPensjonsgivendeInntekt(7, "25")


        respons.statusCode shouldBe HttpStatus.BAD_REQUEST
        verify(exactly = 0) { pensjonsgivendeInntektConsumer.hentPensjonsgivendeInntekt(any()) }
    }

    @Test
    fun `ukjent person gir 404`() {
        every { personRepository.findPersonById(7) } returns null


        val respons = controller.lagKafkaMeldingForPerson(7, "2025", "ny")


        respons.statusCode shouldBe HttpStatus.NOT_FOUND
        skattehendelserProducer.hendelser.shouldBeEmpty()
    }

    @Test
    fun `person uten periode i året gir 400`() {
        every { personRepository.findPersonById(7) } returns personMedPeriode2025()


        val respons = controller.lagKafkaMeldingForPerson(7, "2024", "ny")


        respons.statusCode shouldBe HttpStatus.BAD_REQUEST
        respons.body shouldBe "Person 7 har ingen periode i 2024"
        skattehendelserProducer.hendelser.shouldBeEmpty()
    }

    @Test
    fun `gjelderPeriode som ikke er et årstall gir 400`() {
        val respons = controller.lagKafkaMeldingForPerson(7, "25", "ny")


        respons.statusCode shouldBe HttpStatus.BAD_REQUEST
        respons.body shouldBe "gjelderPeriode må være et årstall"
        skattehendelserProducer.hendelser.shouldBeEmpty()
    }

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
