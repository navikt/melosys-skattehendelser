package no.nav.melosysskattehendelser

import no.nav.melosysskattehendelser.sigrun.AzureContextExchangeFilter
import no.nav.melosysskattehendelser.sigrun.SigrunRestConsumer
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class SkatteHendelserConfig(
    @Value("\${sigrun.rest.url}") private val url: String
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
                .filter(AzureContextExchangeFilter(clientConfigurationProperties, oAuth2AccessTokenService))
                .build()
        )
}