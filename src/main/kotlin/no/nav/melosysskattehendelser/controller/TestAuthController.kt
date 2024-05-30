package no.nav.melosysskattehendelser.controller

import no.nav.security.token.support.core.api.Protected
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@Protected
@RequestMapping("/auth")
class TestAuthController {
    @GetMapping("/test")
    fun testAuth(): ResponseEntity<String> {
        return ResponseEntity.ok("ok")
    }
}