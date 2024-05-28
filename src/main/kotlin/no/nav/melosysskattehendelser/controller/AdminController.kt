package no.nav.melosysskattehendelser.controller

import no.nav.melosysskattehendelser.SkatteHendelsePublisering
import no.nav.security.token.support.core.api.Protected
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@Unprotected // TODO: Skal være protected, men åpner for testing på q2 før vi har lagt på melosys innlogging eller noe
@RequestMapping("/admin")
class AdminController(
    private val skatteHendelsePublisering: SkatteHendelsePublisering
) {
    @PostMapping("/hendelseprosessering/start")
    fun startHendelseProsessering(): ResponseEntity<String> {
        return ResponseEntity.ok("Hendelseprosessering startet")
    }

    @PostMapping("/hendelseprosessering/stop")
    fun stopHendelseProsessering(): ResponseEntity<String> {
        skatteHendelsePublisering.stopProsesseringAvSkattHendelser()
        return ResponseEntity.ok("Hendelseprosessering stoppet")
    }

    @GetMapping("/hendelseprosessering/status")
    fun status(): ResponseEntity<Map<String, Any>> =
        ResponseEntity<Map<String, Any>>(skatteHendelsePublisering.status(), HttpStatus.OK)
}