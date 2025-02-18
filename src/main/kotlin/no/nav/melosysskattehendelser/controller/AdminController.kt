package no.nav.melosysskattehendelser.controller

import mu.KotlinLogging
import no.nav.melosysskattehendelser.SkatteHendelsePublisering
import no.nav.melosysskattehendelser.domain.Periode
import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.domain.SekvensHistorikk
import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse
import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducer
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val log = KotlinLogging.logger { }

@RestController
@Unprotected
@RequestMapping("/admin")
class AdminController(
    private val skatteHendelsePublisering: SkatteHendelsePublisering,
    private val personRepository: PersonRepository,
    private val skattehendelserProducer: SkattehendelserProducer
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

    @GetMapping("/person/{id}")
    fun getPerson(@PathVariable id: Long): ResponseEntity<Map<String, Any?>> {
        val person = personRepository.findPersonById(id) ?: return ResponseEntity(HttpStatus.NOT_FOUND)
        return ResponseEntity(person.toMap(), HttpStatus.OK)
    }

    @GetMapping("/person")
    fun getPersoner(@RequestParam(defaultValue = "10") max: Int): ResponseEntity<List<Map<String, Any?>>> {
        val list: List<Map<String, Any?>> = personRepository.findAll()
            .sortedByDescending { it.id }
            .take(max)
            .map { it.toMap() }
        return ResponseEntity(list, HttpStatus.OK)
    }

    @PostMapping("/kafka")
    fun lagKafkaMelding(@RequestBody melosysSkatteHendelse: MelosysSkatteHendelse): ResponseEntity<String> {
        log.info { "publiserer melding til kafka: $melosysSkatteHendelse" }
        skattehendelserProducer.publiserMelding(melosysSkatteHendelse)
        return ResponseEntity.ok("Melding publisert")
    }

    fun Person.toMap(): Map<String, Any?> = linkedMapOf(
        "id" to this.id,
        "ident" to this.ident,
        "perioder" to this.perioder.map { it.toMap() },
        "sekvensHistorikk" to this.sekvensHistorikk.map { it.toMap() }
    )

    fun Periode.toMap(): Map<String, Any?> = linkedMapOf(
        "id" to this.id,
        "fom" to this.fom,
        "tom" to this.tom
    )

    fun SekvensHistorikk.toMap(): Map<String, Any?> {
        return linkedMapOf(
            "id" to this.id,
            "sekvensnummer" to this.sekvensnummer,
            "antall" to this.antall,
            "sisteHendelseTid" to this.sisteHendelseTid
        )
    }
}
