package no.nav.melosysskattehendelser.melosys.consumer

import no.nav.melosysskattehendelser.PostgresTestContainerBase
import no.nav.melosysskattehendelser.melosys.KafkaTestProducer
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest
@DirtiesContext
@EmbeddedKafka(
    count = 1, controlledShutdown = true, partitions = 1,
    topics = ["teammelosys.melosys.vedtak.v1"],
)
@Import(KafkaTestProducer::class)
@EnableMockOAuth2Server
class VedtakHendelseConsumerTest(
    @Autowired private val kafkaTemplate: KafkaTemplate<String, VedtakHendelseMelding>,
): PostgresTestContainerBase() {

    @Test
    fun name() {
        val vedtakHendelseMelding = VedtakHendelseMelding(
            "456789123",
            Sakstyper.FTRL,
            Sakstemaer.TRYGDEAVGIFT
        )
        kafkaTemplate.send("teammelosys.melosys.vedtak.v1", vedtakHendelseMelding)
    }

}