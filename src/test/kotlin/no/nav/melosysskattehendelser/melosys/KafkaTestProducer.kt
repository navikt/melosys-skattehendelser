package no.nav.melosysskattehendelser.melosys

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.melosysskattehendelser.melosys.consumer.HendelseMelding
import no.nav.melosysskattehendelser.melosys.consumer.VedtakHendelseMelding
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JsonSerializer

@TestConfiguration
class KafkaTestProducer {

    @Bean
    fun testKafkaTemplate(
        kafkaProperties: KafkaProperties,
        objectMapper: ObjectMapper?
    ): KafkaTemplate<String, VedtakHendelseMelding> {
        val props = kafkaProperties.buildProducerProperties()
        val producerFactory: ProducerFactory<String, VedtakHendelseMelding> =
            DefaultKafkaProducerFactory(props, StringSerializer(), JsonSerializer(objectMapper))
        return KafkaTemplate(producerFactory)
    }

}