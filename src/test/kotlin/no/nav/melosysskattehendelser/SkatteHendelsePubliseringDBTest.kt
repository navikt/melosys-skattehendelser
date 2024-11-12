package no.nav.melosysskattehendelser

import io.mockk.mockk
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.domain.SkatteHendelserStatusRepository
import no.nav.melosysskattehendelser.melosys.KafkaConfig
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@ActiveProfiles("test")
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SkatteHendelsePubliseringDBTest(
    @Autowired override var personRepository: PersonRepository,
    @Autowired override var skatteHendelserStatusRepository: SkatteHendelserStatusRepository,
) : SkatteHendelsePubliseringTest() {

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
