package no.nav.melosysskattehendelser.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse
import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducer
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger { }

@RestController
@Unprotected
@RequestMapping("/admin/kafka")
class AdminKafkaController(
    private val skattehendelserProducer: SkattehendelserProducer
) {
    @PostMapping("/sendMelosysHendelse")
    fun sendMessage(@RequestBody hendelse: MelosysSkatteHendelse): ResponseEntity<String> {
        log.info("send melding: $hendelse")
        skattehendelserProducer.publiserMelding(hendelse)

        return ResponseEntity.ok("melding sent")
    }
}
