package no.nav.melosysskattehendelser

import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducer
import no.nav.melosysskattehendelser.sigrun.Hendelse
import no.nav.melosysskattehendelser.sigrun.SigrunRestConsumer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class SkatteHendelserConfig(
    @Value("\${sigrun.rest.url}") private val url: String,
    @Value("\${melosys.kafka.producer.topic}") private val topicName: String
) {

    @Bean
    fun sigrunRestConsumer(webClientBuilder: WebClient.Builder): SigrunRestConsumer {
        return SigrunRestConsumer(
            webClientBuilder
                .baseUrl(url)
                .build()
        )
    }

    @Bean
    fun skatteHendelserProducer(kafkaTemplate: KafkaTemplate<String, Hendelse>): SkattehendelserProducer {
        return SkattehendelserProducer(kafkaTemplate, topicName)
    }
}