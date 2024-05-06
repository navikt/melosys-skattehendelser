package no.nav.melosysskattehendelser

import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableJwtTokenValidation
@EnableOAuth2Client(cacheEnabled = true)
class MelosysSkattehendelserApplication

fun main(args: Array<String>) {
    runApplication<MelosysSkattehendelserApplication>(*args)
}
