package no.nav.melosysskattehendelser.controller

import no.nav.melosysskattehendelser.SkatteHendelsePublisering
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@Unprotected
@RequestMapping("/admin")
class AdminController(
    private val skatteHendelsePublisering: SkatteHendelsePublisering,
    @Value("\${admin.api-key}") private val apiKey: String
) {
    @PostMapping("/hendelseprosessering/start")
    fun startHendelseProsessering(@RequestHeader(API_KEY_HEADER) apiKey: String): ResponseEntity<String> {
        validerApiKey(apiKey)
        skatteHendelsePublisering.asynkronProsesseringAvSkattHendelser()
        return ResponseEntity.ok("Hendelseprosessering startet")
    }

    @PostMapping("/hendelseprosessering/stop")
    fun stopHendelseProsessering(@RequestHeader(API_KEY_HEADER) apiKey: String): ResponseEntity<String> {
        skatteHendelsePublisering.stopProsesseringAvSkattHendelser()
        return ResponseEntity.ok("Hendelseprosessering stoppet")
    }

    @GetMapping("/hendelseprosessering/status")
    fun status(@RequestHeader(API_KEY_HEADER) apiKey: String): ResponseEntity<Map<String, Any>> {
        validerApiKey(apiKey)
        return ResponseEntity<Map<String, Any>>(skatteHendelsePublisering.status(), HttpStatus.OK)
    }

    private fun validerApiKey(apiKey: String) {
        require(apiKey == this.apiKey) { "Ugyldig apikey" }
    }

    companion object {
        const val API_KEY_HEADER = "X-SKATTEHENDELSER-ADMIN-APIKEY"
    }
}
