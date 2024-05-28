package no.nav.melosysskattehendelser.controller

import no.nav.melosysskattehendelser.SkatteHendelsePublisering
import no.nav.security.token.support.core.api.Protected
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@Protected
@RequestMapping("/auth")
class TestAuthController(
    private val skatteHendelsePublisering: SkatteHendelsePublisering
) {
    @GetMapping("/test")
    fun testAuth(): ResponseEntity<String> {
        skatteHendelsePublisering.asynkronProsesseringAvSkattHendelser()
        return ResponseEntity.ok("ok")
    }
}