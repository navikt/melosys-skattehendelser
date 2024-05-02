package no.nav.melosysskattehendelser.sigrun

import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono

class AzureContextExchangeFilter(
    clientConfigurationProperties: ClientConfigurationProperties,
    private val oAuth2AccessTokenService: OAuth2AccessTokenService,
    clientName: String
) : ExchangeFilterFunction {
    private val clientPropertiesForSystem: ClientProperties =
        clientConfigurationProperties.registration[clientName]
            ?: throw RuntimeException("Fant ikke OAuth2-config for $clientName")

    private val systemToken: String
        get() = "Bearer " + oAuth2AccessTokenService.getAccessToken(clientPropertiesForSystem).accessToken

    override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> {
        return next.exchange(
            withClientRequestBuilder(ClientRequest.from(request)).build()
        )
    }

    private fun withClientRequestBuilder(clientRequestBuilder: ClientRequest.Builder): ClientRequest.Builder {
        return clientRequestBuilder.header(HttpHeaders.AUTHORIZATION, systemToken)
    }
}
