package no.nav.melosysskattehendelser.sigrun

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class SigrunRestConsumerConfig(
    @Value("\${sigrun.rest.url}") private val url: String
) {

    @Bean
    fun sigrunRestConsumer(webClientBuilder: WebClient.Builder): SigrunRestConsumer {
        return SigrunRestConsumer(
            webClientBuilder
                .baseUrl(url)
                .build()
        )
    }
}