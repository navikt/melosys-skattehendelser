package no.nav.melosysskattehendelser.controller

import mu.KotlinLogging
import no.nav.melosysskattehendelser.SkatteHendelsePublisering
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

private val log = KotlinLogging.logger { }

@RestController
@Unprotected
@RequestMapping("/admin")
class AdminController(
    private val skatteHendelsePublisering: SkatteHendelsePublisering
) {
    @PostMapping("/hendelseprosessering/start")
    fun startHendelseProsessering(): ResponseEntity<String> {
        log.info("Starter hendelseprosessering")
        skatteHendelsePublisering.asynkronProsesseringAvSkattHendelser()
        return ResponseEntity.ok("Hendelseprosessering startet")
    }

    @PostMapping("/hendelseprosessering/stop")
    fun stopHendelseProsessering(): ResponseEntity<String> {
        log.info("Stopper hendelseprosessering")
        skatteHendelsePublisering.stopProsesseringAvSkattHendelser()
        return ResponseEntity.ok("Hendelseprosessering stoppet")
    }

    @GetMapping("/hendelseprosessering/status")
    fun status(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity<Map<String, Any>>(skatteHendelsePublisering.status(), HttpStatus.OK)
    }
}
