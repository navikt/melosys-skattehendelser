package no.nav.melosysskattehendelser.controller

import no.nav.melosysskattehendelser.SkatteHendelsePublisering
import no.nav.security.token.support.core.api.Protected
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@Protected
@RequestMapping("/admin")
class AdminController(
    private val skatteHendelsePublisering: SkatteHendelsePublisering
) {
    @PostMapping("/hendelseprosessering/start")
    fun startHendelseProsessering(): ResponseEntity<Map<String, Any>> {
        skatteHendelsePublisering.asynkronProsesseringAvSkattHendelser()
        return status()
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