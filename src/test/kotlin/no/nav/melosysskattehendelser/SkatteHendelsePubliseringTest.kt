package no.nav.melosysskattehendelser

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.melosysskattehendelser.domain.*
import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducer
import no.nav.melosysskattehendelser.skatt.Hendelse
import no.nav.melosysskattehendelser.skatt.SkatteHendelserFetcher
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

class SkatteHendelsePubliseringTest {

    private val skatteHendelserFetcher = mockk<SkatteHendelserFetcher>(relaxed = true)
    private val personRepository = mockk<PersonRepository>().apply {
        every { findPersonByIdent(any()) } returns Person(id = 0, ident = "123").apply {
            perioder.add(
                Periode(
                    0,
                    this,
                    LocalDate.of(2022, 1, 1),
                    LocalDate.of(2023, 1, 1)
                )
            )
        }
    }
    private val skatteHendelserStatusRepository = mockk<SkatteHendelserStatusRepository>().apply {
        every { findById(any()) } returns Optional.of(SkatteHendelserSekvens("", 1))
        every { save(any()) } returns mockk()
    }
    private val skattehendelserProducer = mockk<SkattehendelserProducer>().apply {
        every { publiserMelding(any()) } returns Unit
    }

    private val skatteHendelsePublisering =
        SkatteHendelsePublisering(skatteHendelserFetcher, personRepository, skatteHendelserStatusRepository, skattehendelserProducer)

    @Test
    fun `skal publisere melding når vi får hendelse med gjelderperide som finnes i personens perioder - 1`() {
        lagSkatteHendelserFetcherHentHendelserMock("2022")


        skatteHendelsePublisering.prosesserSkattHendelser()


        verify { skattehendelserProducer.publiserMelding(any()) }
    }

    @Test
    fun `skal publisere melding når vi får hendelse med gjelderperide som finnes i personens perioder - 2`() {
        lagSkatteHendelserFetcherHentHendelserMock("2023")


        skatteHendelsePublisering.prosesserSkattHendelser()


        verify { skattehendelserProducer.publiserMelding(any()) }
    }

    @Test
    fun `skal ikke publisere melding når vi får hendelse med gjelderperide som ikke finnes i personens perioder - 1`() {
        lagSkatteHendelserFetcherHentHendelserMock("2021")


        skatteHendelsePublisering.prosesserSkattHendelser()


        verify(exactly = 0) { skattehendelserProducer.publiserMelding(any()) }
    }

    @Test
    fun `skal ikke publisere melding når vi får hendelse med gjelderperide som ikke finnes i personens perioder - 2 `() {
        lagSkatteHendelserFetcherHentHendelserMock("2024")


        skatteHendelsePublisering.prosesserSkattHendelser()


        verify(exactly = 0) { skattehendelserProducer.publiserMelding(any()) }
    }

    private fun lagSkatteHendelserFetcherHentHendelserMock(gjelderPeriode: String) {
        every { skatteHendelserFetcher.hentHendelser(any(), any(), any()) } returns sequence {
            yield(
                Hendelse(
                    gjelderPeriode = gjelderPeriode,
                    identifikator = "123",
                    sekvensnummer = 1,
                    somAktoerid = false,
                    hendelsetype = "ny"
                )
            )
        }
    }
}