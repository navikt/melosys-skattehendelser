package no.nav.melosysskattehendelser.skatt.sigrun

import no.nav.melosysskattehendelser.skatt.SkattHendelseConsumer
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Configuration
class SigrunConsumerConfig(@Value("\${sigrun.rest.url}") private val url: String) {

    @Bean
    fun sigrunRestConsumer(
        webClientBuilder: WebClient.Builder,
        clientConfigurationProperties: ClientConfigurationProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): SkattHendelseConsumer =
        SigrunRestConsumer(
            webClientBuilder
                .baseUrl(url)
                .filter(AzureContextExchangeFilter(clientConfigurationProperties, oAuth2AccessTokenService))
                .filter(ExchangeFilterFunction.ofRequestProcessor { request: ClientRequest ->
                    Mono.just(
                        ClientRequest.from(request)
                            .header("Nav-Call-Id", "rune-tester-melosys-skattehendelser-fra-local-mac") // TODO add MDCOperations
                            .build()
                    )
                })
                .defaultHeaders {
                    it.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    it.add("Nav-Consumer-Id", CONSUMER_ID)
                }
                .build()
        )

    companion object {
        private const val CONSUMER_ID = "srvmelosys"
    }
}