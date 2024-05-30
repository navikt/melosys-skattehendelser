package no.nav.melosysskattehendelser

import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse
import no.nav.melosysskattehendelser.melosys.consumer.VedtakHendelseConsumer
import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate

@Configuration
class SkatteHendelserConfig(
    @Value("\${melosys.kafka.producer.topic}") private val topicName: String
) {


    @Bean
    fun skatteHendelserProducer(kafkaTemplate: KafkaTemplate<String, MelosysSkatteHendelse>) =
        SkattehendelserProducer(kafkaTemplate, topicName)


    @Bean
    fun vedtakMelosysConsumer(vedtakHendelseRepository: PersonRepository) =
        VedtakHendelseConsumer(vedtakHendelseRepository)
}