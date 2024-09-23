package no.nav.melosysskattehendelser

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.melosysskattehendelser.domain.*
import no.nav.melosysskattehendelser.fakes.PersonRepositoryFake
import no.nav.melosysskattehendelser.fakes.SkatteHendelserFetcherFake
import no.nav.melosysskattehendelser.fakes.SkatteHendelserStatusRepositoryFake
import no.nav.melosysskattehendelser.fakes.SkattehendelserProducerFake
import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse
import no.nav.melosysskattehendelser.skatt.Hendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SkatteHendelsePubliseringTest {

    private val skatteHendelserFetcher = SkatteHendelserFetcherFake()
    private val personRepository = PersonRepositoryFake()
    private val skatteHendelserStatusRepository = SkatteHendelserStatusRepositoryFake()
    private val skattehendelserProducer = SkattehendelserProducerFake()

    private val skatteHendelsePublisering =
        SkatteHendelsePublisering(skatteHendelserFetcher, personRepository, skatteHendelserStatusRepository, skattehendelserProducer)

    @BeforeEach
    fun setUp() {
        personRepository
            .reset()
            .apply {
                items.add(Person(id = 0, ident = "123").apply {
                    perioder.add(
                        Periode(
                            0,
                            this,
                            LocalDate.of(2022, 1, 1),
                            LocalDate.of(2023, 1, 1)
                        )
                    )
                })
            }
        skatteHendelserStatusRepository.reset()
        skatteHendelserFetcher.reset()
        skattehendelserProducer.reset()
    }

    @Test
    fun `skal publisere melding når vi får hendelse med gjelderperide som finnes i personens perioder - 1`() {
        lagSkatteHendelserFetcherHentHendelserMock("2022")


        skatteHendelsePublisering.prosesserSkattHendelser()


        skattehendelserProducer.hendelser.single().shouldBe(
            MelosysSkatteHendelse(
                "2022",
                "123",
                "ny"
            )
        )
    }

    @Test
    fun `skal ikke publisere melding når vi får hendelse som er kjørt før`() {
        lagSkatteHendelserFetcherHentHendelserMock("2022")
        personRepository.items.single().sekvensnummer = 1


        skatteHendelsePublisering.prosesserSkattHendelser()


        skattehendelserProducer.hendelser.shouldBeEmpty()
    }


    @Test
    fun `skal publisere melding når vi får hendelse med gjelderperide som finnes i personens perioder - 2`() {
        lagSkatteHendelserFetcherHentHendelserMock("2023")


        skatteHendelsePublisering.prosesserSkattHendelser()


        skattehendelserProducer.hendelser.single().shouldBe(
            MelosysSkatteHendelse(
                "2023",
                "123",
                "ny"
            )
        )
    }

    @Test
    fun `skal ikke publisere melding når vi får hendelse med gjelderperide som ikke finnes i personens perioder - 1`() {
        lagSkatteHendelserFetcherHentHendelserMock("2021")


        skatteHendelsePublisering.prosesserSkattHendelser()


        skattehendelserProducer.hendelser.shouldBeEmpty()
    }

    @Test
    fun `skal ikke publisere melding når vi får hendelse med gjelderperide som ikke finnes i personens perioder - 2 `() {
        lagSkatteHendelserFetcherHentHendelserMock("2024")


        skatteHendelsePublisering.prosesserSkattHendelser()


        skattehendelserProducer.hendelser.shouldBeEmpty()
    }


    private fun lagSkatteHendelserFetcherHentHendelserMock(gjelderPeriode: String): SkatteHendelserFetcherFake =
        skatteHendelserFetcher.reset().apply {
            hendelser.add(
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