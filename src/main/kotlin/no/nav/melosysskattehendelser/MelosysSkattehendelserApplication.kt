package no.nav.melosysskattehendelser

import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableJwtTokenValidation
@EnableOAuth2Client(cacheEnabled = true)
@EnableScheduling
@EnableAsync
class MelosysSkattehendelserApplication

fun main(args: Array<String>) {
    runApplication<MelosysSkattehendelserApplication>(*args)
}
