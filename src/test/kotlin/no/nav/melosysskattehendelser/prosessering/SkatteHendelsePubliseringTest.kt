package no.nav.melosysskattehendelser.prosessering

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
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
import no.nav.melosysskattehendelser.LoggingTestUtils.withLogCapture
import no.nav.melosysskattehendelser.domain.SekvensHistorikk
import no.nav.melosysskattehendelser.domain.SkatteHendelserSekvens
import no.nav.melosysskattehendelser.domain.SkatteHendelserStatusRepository
import no.nav.melosysskattehendelser.domain.SkatteHendelserStatusRepositoryFake
import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse
import no.nav.melosysskattehendelser.melosys.consumer.KafkaContainerMonitor
import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducerFake
import no.nav.melosysskattehendelser.metrics.MeasuredMetricsProvider
import no.nav.melosysskattehendelser.metrics.Metrikker
import no.nav.melosysskattehendelser.skatt.Hendelse
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntekt
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektConsumerFake
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektResponse
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
    private val pensjonsgivendeInntektConsumer = PensjonsgivendeInntektConsumerFake()

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
        pensjonsgivendeInntektConsumer.reset()
        personRepository.deleteAll()
        skatteHendelserStatusRepository.deleteAll()
        skatteHendelserFetcher.reset()
        skattehendelserProducer.reset()
    }

    @Test
    fun `skal publisere melding når vi får hendelse med gjelderperide som finnes i personens perioder - 1`() {
        pensjonsgivendeInntektConsumer.reset().leggTilPensjonsgivendeInntekt(
            inntektsaar = "2022",
        )
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
        pensjonsgivendeInntektConsumer.leggTilPensjonsgivendeInntekt(
            inntektsaar = "2020",
        )
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
        pensjonsgivendeInntektConsumer.leggTilPensjonsgivendeInntekt(
            inntektsaar = "2022",
        )
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022")


        skatteHendelsePublisering.prosesserSkattHendelser()


        skatteHendelserStatusRepository.findAll()
            .single()
            .sekvensnummer shouldBe 2
    }


    @Test
    fun `skal oppdatere brukers sekvensnummer ved publisering`() {
        pensjonsgivendeInntektConsumer.leggTilPensjonsgivendeInntekt(
            inntektsaar = "2022",
        )
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
    fun `skal lagre pensjonsgivende inntekt som er ulik`() {
        pensjonsgivendeInntektConsumer
            .leggTilPensjonsgivendeInntekt(inntektsaar = "2023")
            .leggTilPensjonsgivendeInntekt(inntektsaar = "2023") { response: PensjonsgivendeInntektResponse ->
                response.copy(pensjonsgivendeInntekt = response.pensjonsgivendeInntekt.map { inntekt: PensjonsgivendeInntekt ->
                    inntekt.copy(pensjonsgivendeInntektAvLoennsinntekt = "2001")
                })
            }

        skatteHendelserFetcher.hendelser.addAll(
            (1..2).map
            {
                Hendelse(
                    gjelderPeriode = "2023",
                    identifikator = "123",
                    sekvensnummer = it.toLong(),
                    somAktoerid = false
                )
            }
        )


        skatteHendelsePublisering.prosesserSkattHendelser()


        skattehendelserProducer.hendelser.shouldHaveSize(2)
        pensjonsgivendeInntektRepository.findAll().shouldHaveSize(2)
    }

    @Test
    fun `skal lagre publiserings historikk ved ny hendelse`() {
        pensjonsgivendeInntektConsumer
            .leggTilPensjonsgivendeInntekt(inntektsaar = "2023")
            .leggTilPensjonsgivendeInntekt(inntektsaar = "2023") { response: PensjonsgivendeInntektResponse ->
                response.copy(pensjonsgivendeInntekt = response.pensjonsgivendeInntekt.map { inntekt: PensjonsgivendeInntekt ->
                    inntekt.copy(pensjonsgivendeInntektAvLoennsinntekt = "2001")
                })
            }

        skatteHendelserFetcher.hendelser.addAll(
            (1..2).map
            {
                Hendelse(
                    gjelderPeriode = "2023",
                    identifikator = "123",
                    sekvensnummer = it.toLong(),
                    somAktoerid = false
                )
            }
        )


        skatteHendelsePublisering.prosesserSkattHendelser()


        skattehendelserProducer.hendelser.shouldHaveSize(2)
        pensjonsgivendeInntektRepository.findAll().shouldHaveSize(2)
        personRepository.findAll()
            .shouldHaveSize(1)
            .single()
            .perioder.shouldHaveSize(1)
            .single().publiseringsHistorikk
            .shouldHaveSize(2).toList().run {
                this[0].sekvensnummer shouldBe 1
                this[1].sekvensnummer shouldBe 2
            }
    }

    @Test
    fun `skal lagre pensjonsgivende inntekt ved slettet og ikke publisere hendelse`() {
        pensjonsgivendeInntektConsumer
            .leggTilPensjonsgivendeInntekt(inntektsaar = "2023") { response: PensjonsgivendeInntektResponse ->
                response.copy(
                    slettet = true,
                    pensjonsgivendeInntekt = emptyList()
                )
            }

        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2023")


        skatteHendelsePublisering.prosesserSkattHendelser()


        skattehendelserProducer.hendelser.shouldBeEmpty()
        pensjonsgivendeInntektRepository.findAll().shouldHaveSize(1)
    }

    @Test
    fun `skal hånter 2 slettinger og ikke publisere hendelse`() {
        pensjonsgivendeInntektConsumer
            .leggTilPensjonsgivendeInntekt(inntektsaar = "2023") { response: PensjonsgivendeInntektResponse ->
                response.copy(
                    slettet = true,
                    pensjonsgivendeInntekt = emptyList()
                )
            }
            .leggTilPensjonsgivendeInntekt(inntektsaar = "2023") { response: PensjonsgivendeInntektResponse ->
                response.copy(
                    slettet = true,
                    pensjonsgivendeInntekt = emptyList()
                )
            }

        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode(gjelderPeriode = "2023", sekvensnummer = 1)
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode(gjelderPeriode = "2023", sekvensnummer = 2)


        skatteHendelsePublisering.prosesserSkattHendelser()


        skattehendelserProducer.hendelser.shouldBeEmpty()
        pensjonsgivendeInntektRepository.findAll().shouldHaveSize(1)
    }

    @Test
    fun `skal håntere sletting etter vanlig melding og ikke publisere hendelse`() {
        pensjonsgivendeInntektConsumer
            .leggTilPensjonsgivendeInntekt(inntektsaar = "2023")
            .leggTilPensjonsgivendeInntekt(inntektsaar = "2023") { response: PensjonsgivendeInntektResponse ->
                response.copy(
                    slettet = true,
                    pensjonsgivendeInntekt = emptyList()
                )
            }
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode(gjelderPeriode = "2023", sekvensnummer = 1)
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode(gjelderPeriode = "2023", sekvensnummer = 2)


        skatteHendelsePublisering.prosesserSkattHendelser()


        skattehendelserProducer.hendelser.shouldHaveSize(1)
        pensjonsgivendeInntektRepository.findAll().onEach {
            println(it.duplikater)
            println(it.historiskInntekt)
        }.shouldHaveSize(2)
    }


    @Test
    fun `skal ikke publisere melding når vi får pensjonsgivende inntekt som er lik`() {
        pensjonsgivendeInntektConsumer
            .leggTilPensjonsgivendeInntekt(inntektsaar = "2022")
            .leggTilPensjonsgivendeInntekt(inntektsaar = "2022")

        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode(gjelderPeriode = "2022", sekvensnummer = 1)
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode(gjelderPeriode = "2022", sekvensnummer = 2)


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
        pensjonsgivendeInntektConsumer
            .leggTilPensjonsgivendeInntekt(inntektsaar = "2022")
            .leggTilPensjonsgivendeInntekt(inntektsaar = "2022")

        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode(gjelderPeriode = "2022", sekvensnummer = 1)
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode(gjelderPeriode = "2022", sekvensnummer = 2)


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

    @Test
    fun `omlesing med fraDato starter på sekvensnummeret for datoen og ikke på markøren`() {
        skatteHendelserStatusRepository.save(SkatteHendelserSekvens("fake", 10))
        skatteHendelserFetcher.sekvensnummerForDato = 5
        pensjonsgivendeInntektConsumer.leggTilPensjonsgivendeInntekt(inntektsaar = "2022")
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022", sekvensnummer = 4)
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022", sekvensnummer = 5)


        skatteHendelsePublisering.prosesserSkattHendelser(
            SkatteHendelsePubliseringOptions(fraDato = LocalDate.of(2025, 1, 1))
        )


        skattehendelserProducer.hendelser.shouldHaveSize(1)
        personRepository.findAll().single().sekvensHistorikk.single().sekvensnummer shouldBe 5
    }

    @Test
    fun `omlesing behandler ingen hendelse fra markøren og oppover og skriver ikke markøren`() {
        skatteHendelserStatusRepository.save(SkatteHendelserSekvens("fake", 10))
        skatteHendelserFetcher.batchSize = 1
        pensjonsgivendeInntektConsumer.leggTilPensjonsgivendeInntekt(inntektsaar = "2022")
        listOf(5L, 10L, 11L).forEach { skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022", it) }


        val logg = withLogCapture { logEvents ->
            skatteHendelsePublisering.prosesserSkattHendelser(SkatteHendelsePubliseringOptions(fraSekvensnummer = 5))
            logEvents.map { it.formattedMessage }
        }


        skattehendelserProducer.hendelser.shouldHaveSize(1)
        skatteHendelserFetcher.høyesteUtgitt shouldBe 10
        skatteHendelserStatusRepository.findById("fake").get().sekvensnummer shouldBe 10
        skatteHendelserStatusRepository.findById("fake-omlesing").get().sekvensnummer shouldBe 10
        logg.shouldContain("Omlesingen stoppet ved markøren 10, siste sekvensnummer 6")
    }

    @Test
    fun `omlesing stoppet med stop logger stoppet før markøren og lagrer fremdriften`() {
        skatteHendelserStatusRepository.save(SkatteHendelserSekvens("fake", 10))
        skatteHendelserFetcher.batchSize = 1
        pensjonsgivendeInntektConsumer.leggTilPensjonsgivendeInntekt(inntektsaar = "2022")
        listOf(5L, 6L).forEach { skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022", it) }
        skatteHendelserFetcher.vedUtgivelse = {
            if (it.sekvensnummer == 6L) skatteHendelsePublisering.stopProsesseringAvSkattHendelser()
        }


        val logg = withLogCapture { logEvents ->
            skatteHendelsePublisering.prosesserSkattHendelser(SkatteHendelsePubliseringOptions(fraSekvensnummer = 5))
            logEvents.map { it.formattedMessage }
        }


        skattehendelserProducer.hendelser.shouldHaveSize(1)
        skatteHendelserStatusRepository.findById("fake").get().sekvensnummer shouldBe 10
        skatteHendelserStatusRepository.findById("fake-omlesing").get().sekvensnummer shouldBe 6
        logg.shouldContain("Omlesingen stoppet før markøren 10, siste sekvensnummer 6")
    }

    @Test
    fun `omlesing publiserer for person uten historikk og teller hendelser personen har sett som duplikater`() {
        skatteHendelserStatusRepository.save(SkatteHendelserSekvens("fake", 10))
        pensjonsgivendeInntektConsumer.leggTilPensjonsgivendeInntekt(inntektsaar = "2022")
        listOf(5L, 6L).forEach { skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022", it) }
        personRepository.findAll().single().let { person ->
            person.sekvensHistorikk.add(SekvensHistorikk(sekvensnummer = 6, antall = 0, person = person))
            personRepository.save(person)
        }


        skatteHendelsePublisering.prosesserSkattHendelser(
            SkatteHendelsePubliseringOptions(fraDato = LocalDate.of(2025, 1, 1))
        )


        skattehendelserProducer.hendelser.shouldHaveSize(1)
        skatteHendelsePublisering.status("", false).run {
            get("publisert") shouldBe 1
            get("duplikater") shouldBe 1
        }
    }

    @Test
    fun `omlesing skriver startpunktet i fremdriftsraden før første hendelse`() {
        skatteHendelserStatusRepository.save(SkatteHendelserSekvens("fake", 10))
        skatteHendelserStatusRepository.save(SkatteHendelserSekvens("fake-omlesing", 99))
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022", sekvensnummer = 5)
        skatteHendelserFetcher.vedUtgivelse = { skatteHendelsePublisering.stopProsesseringAvSkattHendelser() }


        skatteHendelsePublisering.prosesserSkattHendelser(SkatteHendelsePubliseringOptions(fraSekvensnummer = 5))


        skatteHendelserStatusRepository.findById("fake-omlesing").get().sekvensnummer shouldBe 5
    }

    @Test
    fun `omlesing som når markøren regnes som ferdig selv om stop kommer etterpå`() {
        skatteHendelserStatusRepository.save(SkatteHendelserSekvens("fake", 10))
        pensjonsgivendeInntektConsumer.leggTilPensjonsgivendeInntekt(inntektsaar = "2022")
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022", sekvensnummer = 5)
        skatteHendelserFetcher.vedSlutt = { skatteHendelsePublisering.stopProsesseringAvSkattHendelser() }


        skatteHendelsePublisering.prosesserSkattHendelser(SkatteHendelsePubliseringOptions(fraSekvensnummer = 5))


        skatteHendelserStatusRepository.findById("fake-omlesing").get().sekvensnummer shouldBe 10
    }

    @Test
    fun `omlesing som når markøren regnes som ferdig selv om stop kommer mens markørhendelsen hentes`() {
        skatteHendelserStatusRepository.save(SkatteHendelserSekvens("fake", 10))
        skatteHendelserFetcher.batchSize = 1
        pensjonsgivendeInntektConsumer.leggTilPensjonsgivendeInntekt(inntektsaar = "2022")
        listOf(5L, 10L).forEach { skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022", it) }
        skatteHendelserFetcher.vedUtgivelse = {
            if (it.sekvensnummer == 10L) skatteHendelsePublisering.stopProsesseringAvSkattHendelser()
        }


        skatteHendelsePublisering.prosesserSkattHendelser(SkatteHendelsePubliseringOptions(fraSekvensnummer = 5))


        skatteHendelserStatusRepository.findById("fake-omlesing").get().sekvensnummer shouldBe 10
    }

    @Test
    fun `omlesing uten hendelser fram til markøren skriver markøren`() {
        skatteHendelserStatusRepository.save(SkatteHendelserSekvens("fake", 10))


        val logg = withLogCapture { logEvents ->
            skatteHendelsePublisering.prosesserSkattHendelser(SkatteHendelsePubliseringOptions(fraSekvensnummer = 5))
            logEvents.map { it.formattedMessage }
        }


        skatteHendelserStatusRepository.findById("fake-omlesing").get().sekvensnummer shouldBe 10
        logg.shouldContain("Omlesingen stoppet ved markøren 10, siste sekvensnummer 5")
    }

    @Test
    fun `omlesing uten lagret markør feiler`() {
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022", sekvensnummer = 5)


        shouldThrow<IllegalStateException> {
            skatteHendelsePublisering.prosesserSkattHendelser(SkatteHendelsePubliseringOptions(fraSekvensnummer = 5))
        }.message shouldBe "Omlesing krever lagret sekvensnummer for fake"
        skattehendelserProducer.hendelser.shouldBeEmpty()
    }

    @Test
    fun `status viser registreringstidspunkt for første og siste leste hendelse`() {
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2030", sekvensnummer = 1, registreringstidspunkt = "2025-06-01T10:00:00")
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2030", sekvensnummer = 2, registreringstidspunkt = "2025-07-15T12:30:00")
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2030", sekvensnummer = 3, registreringstidspunkt = null)


        skatteHendelsePublisering.prosesserSkattHendelser()


        skatteHendelsePublisering.status("", false).run {
            get("startSekvensnummer") shouldBe 1L
            get("førsteRegistreringstidspunkt") shouldBe "2025-06-01T10:00:00"
            get("sisteRegistreringstidspunkt") shouldBe "2025-07-15T12:30:00"
            get("markør") shouldBe null
            get("omlesingFremdriftProsent") shouldBe null
        }
    }

    @Test
    fun `status viser fremdrift i prosent mot markøren når omlesingen stoppes underveis`() {
        skatteHendelserStatusRepository.save(SkatteHendelserSekvens("fake", 10))
        skatteHendelserFetcher.batchSize = 1
        pensjonsgivendeInntektConsumer.leggTilPensjonsgivendeInntekt(inntektsaar = "2022")
        listOf(5L, 6L).forEach { skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022", it) }
        skatteHendelserFetcher.vedUtgivelse = {
            if (it.sekvensnummer == 6L) skatteHendelsePublisering.stopProsesseringAvSkattHendelser()
        }


        skatteHendelsePublisering.prosesserSkattHendelser(SkatteHendelsePubliseringOptions(fraSekvensnummer = 5))


        skatteHendelsePublisering.status("", false).run {
            get("startSekvensnummer") shouldBe 5L
            get("markør") shouldBe 10L
            get("sisteSekvensnummer") shouldBe 6L
            get("omlesingFremdriftProsent") shouldBe 20
        }
    }

    private fun SkatteHendelserFetcherFake.leggTilHendelseMedGjelderPeriode(
        gjelderPeriode: String,
        sekvensnummer: Long = 1,
        registreringstidspunkt: String? = null
    ) {
        hendelser.add(
            Hendelse(
                gjelderPeriode = gjelderPeriode,
                identifikator = "123",
                sekvensnummer = sekvensnummer,
                somAktoerid = false,
                hendelsetype = "ny",
                registreringstidspunkt = registreringstidspunkt
            )
        )
    }
}