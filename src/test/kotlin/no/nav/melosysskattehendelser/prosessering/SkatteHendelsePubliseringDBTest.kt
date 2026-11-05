package no.nav.melosysskattehendelser.prosessering

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.melosysskattehendelser.PostgresTestContainer
import no.nav.melosysskattehendelser.domain.PensjonsgivendeInntektRepository
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.domain.SkatteHendelserSekvens
import no.nav.melosysskattehendelser.domain.SkatteHendelserStatusRepository
import no.nav.melosysskattehendelser.melosys.KafkaConfig
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionOperations

@ActiveProfiles("test")
@SpringBootTest
@EnableMockOAuth2Server
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SkatteHendelsePubliseringDBTest(
    @Autowired override var personRepository: PersonRepository,
    @Autowired statusRepository: SkatteHendelserStatusRepository,
    @Autowired override var pensjonsgivendeInntektRepository: PensjonsgivendeInntektRepository,
    @Autowired override var transaksjoner: TransactionOperations,
) : SkatteHendelsePubliseringTest() {

    private val statusRepositoryMedFeil = StatusRepositoryMedFeil(statusRepository)
    override var skatteHendelserStatusRepository: SkatteHendelserStatusRepository = statusRepositoryMedFeil

    // Denne testen og den neste går bare mot Postgres. PersonRepositoryFake gir tilbake samme objekt,
    // så sekvenshistorikken fra den feilede kjøringen ville blitt med inn i neste kjøring.
    @Test
    fun `hendelsen sendes i neste kjøring når sendingen feiler`() {
        pensjonsgivendeInntektConsumer
            .leggTilPensjonsgivendeInntekt(inntektsaar = "2022")
            .leggTilPensjonsgivendeInntekt(inntektsaar = "2022")
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022")
        skattehendelserProducer.feilVedNesteSending = IllegalStateException("Kafka er nede")


        shouldThrow<IllegalStateException> { skatteHendelsePublisering.prosesserSkattHendelser() }
        skatteHendelsePublisering.prosesserSkattHendelser()


        skattehendelserProducer.hendelser.shouldHaveSize(1)
        pensjonsgivendeInntektRepository.findAll().single().duplikater shouldBe 0
        personRepository.findAll().single().perioder.single().publiseringsHistorikk.shouldHaveSize(1)
        skatteHendelserStatusRepository.findById("fake").get().sekvensnummer shouldBe 2
    }

    @Test
    fun `hendelsen sendes på nytt når lagringen etter sendingen feiler`() {
        pensjonsgivendeInntektConsumer
            .leggTilPensjonsgivendeInntekt(inntektsaar = "2022")
            .leggTilPensjonsgivendeInntekt(inntektsaar = "2022")
        skatteHendelserFetcher.leggTilHendelseMedGjelderPeriode("2022")
        statusRepositoryMedFeil.feilVedNesteLagring = IllegalStateException("Databasen er nede")


        shouldThrow<IllegalStateException> { skatteHendelsePublisering.prosesserSkattHendelser() }
        skatteHendelsePublisering.status("", false)["sisteSekvensnummer"] shouldBe 0L
        skatteHendelsePublisering.prosesserSkattHendelser()


        skattehendelserProducer.hendelser.shouldHaveSize(2)
        pensjonsgivendeInntektRepository.findAll().single().duplikater shouldBe 0
        personRepository.findAll().single().run {
            perioder.single().publiseringsHistorikk.shouldHaveSize(1)
            sekvensHistorikk.shouldHaveSize(1)
        }
        skatteHendelserStatusRepository.findById("fake").get().sekvensnummer shouldBe 2
    }

    class StatusRepositoryMedFeil(
        private val repository: SkatteHendelserStatusRepository
    ) : SkatteHendelserStatusRepository by repository {
        var feilVedNesteLagring: Exception? = null

        override fun <S : SkatteHendelserSekvens> save(entity: S): S {
            feilVedNesteLagring?.let {
                feilVedNesteLagring = null
                throw it
            }
            return repository.save(entity)
        }
    }

    @TestConfiguration
    class Config {
        @Bean // Denne er nødvendig for å slippe å kjøre med @EmbeddedKafka
        fun kafkaConfig() = mockk<KafkaConfig>(relaxed = true)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            PostgresTestContainer.registerPostgresProperties(registry)
        }
    }
}