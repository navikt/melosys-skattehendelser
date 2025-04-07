package no.nav.melosysskattehendelser.prosessering

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.melosysskattehendelser.domain.PensjonsgivendeInntektRepository
import no.nav.melosysskattehendelser.domain.PensjonsgivendeInntektRepositoryFake
import no.nav.melosysskattehendelser.domain.Periode
import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.domain.PersonRepositoryFake
import no.nav.melosysskattehendelser.domain.SekvensHistorikk
import no.nav.melosysskattehendelser.domain.SkatteHendelserStatusRepository
import no.nav.melosysskattehendelser.domain.SkatteHendelserStatusRepositoryFake
import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse
import no.nav.melosysskattehendelser.melosys.consumer.KafkaContainerMonitor
import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducerFake
import no.nav.melosysskattehendelser.metrics.MeasuredMetricsProvider
import no.nav.melosysskattehendelser.metrics.Metrikker
import no.nav.melosysskattehendelser.skatt.Hendelse
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektConsumer
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektConsumerFake
import no.nav.melosysskattehendelser.skatt.SkatteHendelserFetcherFake
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

open class SkatteHendelsePubliseringTest {
    protected open var personRepository: PersonRepository = PersonRepositoryFake()
    protected open var pensjonsgivendeInntektRepository: PensjonsgivendeInntektRepository =
        PensjonsgivendeInntektRepositoryFake()
    protected open var skatteHendelserStatusRepository: SkatteHendelserStatusRepository =
        SkatteHendelserStatusRepositoryFake()
    private val skattehendelserProducer: SkattehendelserProducerFake = SkattehendelserProducerFake()
    private val skatteHendelserFetcher: SkatteHendelserFetcherFake = SkatteHendelserFetcherFake()
    private val pensjonsgivendeInntektConsumer: PensjonsgivendeInntektConsumer = PensjonsgivendeInntektConsumerFake()

    private val skatteHendelsePublisering by lazy {
        SkatteHendelsePublisering(
            skatteHendelserFetcher,
            personRepository,
            skatteHendelserStatusRepository,
            pensjonsgivendeInntektRepository,
            skattehendelserProducer,
            pensjonsgivendeInntektConsumer,
            Metrikker(),
            mockk<PersonFinderSelector>().apply { every { find(any()) } returns PersonFinderDB(personRepository) },
            mockk<KafkaContainerMonitor>().apply {
                every { isKafkaContainerRunning() } returns true
            },
            mockk<MeasuredMetricsProvider>(relaxed = true)
        )
    }

    @BeforeEach
    fun setUp() {
        personRepository
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
    }

    @AfterEach
    fun tearDown() {
        personRepository.deleteAll()
        skatteHendelserStatusRepository.deleteAll()
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
            personRepository.save(person)
        }


        skatteHendelsePublisering.prosesserSkattHendelser()


        skattehendelserProducer.hendelser.shouldBeEmpty()
    }

    @Test
    fun `skal ikke publisere melding når vi får pensjonsgivende inntekt som er lik`() {
        skatteHendelserFetcher.hendelser.addAll(
            (1..2).map {
                Hendelse(
                    gjelderPeriode = "2023",
                    identifikator = "123",
                    sekvensnummer = it.toLong(),
                    somAktoerid = false
                )
            }
        )


        skatteHendelsePublisering.prosesserSkattHendelser()


        skattehendelserProducer.hendelser.shouldHaveSize(1)
        pensjonsgivendeInntektRepository.findAll().single().run {
            duplikater shouldBe 1
        }
    }


    @Test
    fun `skal øke antall når hendelse med samme sekvensnummer er kjørt før`() {
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022")
        val person = personRepository.findAll().single()
        person.sekvensHistorikk.add(
            SekvensHistorikk(sekvensnummer = 1, antall = 0, person = person)
        )
        personRepository.save(person)


        skatteHendelsePublisering.prosesserSkattHendelser()


        personRepository.findAll().single().sekvensHistorikk.single().antall shouldBe 1
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

    private fun SkatteHendelserFetcherFake.leggTilHendelseMedGjelderPeriode(gjelderPeriode: String) {
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