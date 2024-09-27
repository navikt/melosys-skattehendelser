package no.nav.melosysskattehendelser

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.melosysskattehendelser.domain.*
import no.nav.melosysskattehendelser.domain.PersonRepositoryFake
import no.nav.melosysskattehendelser.skatt.FakeSkatteHendelserFetcherAPI
import no.nav.melosysskattehendelser.domain.SkatteHendelserStatusRepositoryFake
import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducerFake
import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse
import no.nav.melosysskattehendelser.skatt.Hendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SkatteHendelsePubliseringTest {

    private val skatteHendelserFetcher = FakeSkatteHendelserFetcherAPI()
    private val personRepository = PersonRepositoryFake()
    private val skatteHendelserStatusRepository = SkatteHendelserStatusRepositoryFake()
    private val skattehendelserProducer = SkattehendelserProducerFake()

    private val skatteHendelsePublisering =
        SkatteHendelsePublisering(
            skatteHendelserFetcher,
            personRepository,
            skatteHendelserStatusRepository,
            skattehendelserProducer
        )

    @BeforeEach
    fun setUp() {
        personRepository
            .reset()
            .apply {
                save(
                    Person(id = 0, ident = "123").apply {
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
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022")


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
    fun `skal publisere melding når vi får hendelse med gjelderperide som finnes i personens perioder - 2`() {
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2023")


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
    fun `skal oppdatere sekvensnummer etter publisering`() {
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022")


        skatteHendelsePublisering.prosesserSkattHendelser()


        skatteHendelserStatusRepository.findAll()
            .single()
            .sekvensnummer shouldBe 2
    }


    @Test
    fun `skal oppdatere brukers sekvensnummer ved publisering`() {
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022")


        skatteHendelsePublisering.prosesserSkattHendelser()


        personRepository.findAll().single()
            .sekvensHistorikk.single()
            .sekvensnummer shouldBe 1
    }

    @Test
    fun `skal ikke publisere melding når vi får hendelse som er kjørt før`() {
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022")
        personRepository.findAll().single().let { person ->
            person.sekvensHistorikk.add(
                SekvensHistorikk(sekvensnummer = 1, antall = 0, person = person)
            )
        }


        skatteHendelsePublisering.prosesserSkattHendelser()


        skattehendelserProducer.hendelser.shouldBeEmpty()
    }

    @Test
    fun `skal øke antall når hendelse med samme sekvensnummer er kjørt før`() {
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022")
        val sekvensHistorie = personRepository.findAll().single().let { person ->
            SekvensHistorikk(sekvensnummer = 1, antall = 0, person = person).also { sekvensHistorie ->
                person.sekvensHistorikk.add(sekvensHistorie)
            }
        }


        skatteHendelsePublisering.prosesserSkattHendelser()


        sekvensHistorie.antall shouldBe 1
    }

    @Test
    fun `skal få flere treff i sekvensHistorikk ved samme identifikator`() {
        skatteHendelserFetcher.hendelser.addAll(
            (1..2).map {
                Hendelse(
                    gjelderPeriode = "2022",
                    identifikator = "123",
                    sekvensnummer = it.toLong(),
                    somAktoerid = false
                )
            }
        )


        skatteHendelsePublisering.prosesserSkattHendelser()


        personRepository.findAll().single().sekvensHistorikk.shouldHaveSize(2)
    }

    @Test
    fun `skal ikke publisere melding når vi får hendelse med gjelderperide som ikke finnes i personens perioder - 1`() {
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2021")


        skatteHendelsePublisering.prosesserSkattHendelser()


        skattehendelserProducer.hendelser.shouldBeEmpty()
    }

    @Test
    fun `skal ikke publisere melding når vi får hendelse med gjelderperide som ikke finnes i personens perioder - 2 `() {
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2024")


        skatteHendelsePublisering.prosesserSkattHendelser()


        skattehendelserProducer.hendelser.shouldBeEmpty()
    }

    private fun FakeSkatteHendelserFetcherAPI.leggTilHendelseMedGjelderPeriode(gjelderPeriode: String) {
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