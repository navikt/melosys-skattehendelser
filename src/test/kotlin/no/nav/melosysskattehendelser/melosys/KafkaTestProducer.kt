package no.nav.melosysskattehendelser.melosys

import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate

@TestConfiguration
class KafkaTestProducer {
    @Bean
    fun kafkaStringTemplate(kafkaProperties: KafkaProperties): KafkaTemplate<String, String> = KafkaTemplate(
        DefaultKafkaProducerFactory(kafkaProperties.buildProducerProperties(null))
    )
}