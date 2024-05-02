package no.nav.melosysskattehendelser

import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.melosys.consumer.VedtakHendelseConsumer
import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducer
import no.nav.melosysskattehendelser.sigrun.AzureContextExchangeFilter
import no.nav.melosysskattehendelser.sigrun.Hendelse
import no.nav.melosysskattehendelser.sigrun.SigrunRestConsumer
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class SkatteHendelserConfig(
    @Value("\${sigrun.rest.url}") private val url: String,
    @Value("\${sigrun.rest.clientName}") private val clientName: String,
    @Value("\${melosys.kafka.producer.topic}") private val topicName: String
) {

    @Bean
    fun sigrunRestConsumer(
        webClientBuilder: WebClient.Builder,
        clientConfigurationProperties: ClientConfigurationProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): SigrunRestConsumer =
        SigrunRestConsumer(
            webClientBuilder
                .baseUrl(url)
                .filter(AzureContextExchangeFilter(clientConfigurationProperties, oAuth2AccessTokenService, "sigrun"))
                .build()
        )

    @Bean
    fun skatteHendelserProducer(kafkaTemplate: KafkaTemplate<String, Hendelse>): SkattehendelserProducer =
        SkattehendelserProducer(kafkaTemplate, topicName)


    @Bean
    fun vedtakMelosysConsumer(vedtakHendelseRepository: PersonRepository): VedtakHendelseConsumer =
        VedtakHendelseConsumer(vedtakHendelseRepository)
}