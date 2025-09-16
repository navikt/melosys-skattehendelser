package no.nav.melosysskattehendelser.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.melosysskattehendelser.domain.PensjonsgivendeInntektRepository
import no.nav.melosysskattehendelser.prosessering.SkatteHendelsePublisering
import no.nav.melosysskattehendelser.domain.Periode
import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.domain.PubliseringsHistorikk
import no.nav.melosysskattehendelser.domain.SekvensHistorikk
import no.nav.melosysskattehendelser.melosys.consumer.KafkaContainerMonitor
import no.nav.melosysskattehendelser.melosys.producer.SkattehendelserProducer
import no.nav.melosysskattehendelser.melosys.MelosysSkatteHendelse
import no.nav.melosysskattehendelser.prosessering.JobConfirmationService
import no.nav.melosysskattehendelser.prosessering.SkatteHendelsePubliseringOptions
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
    private val pensjonsgivendeInntektRepository: PensjonsgivendeInntektRepository,
    private val kafkaContainerMonitor: KafkaContainerMonitor,
    private val skattehendelserProducer: SkattehendelserProducer,
    private val jobConfirmationService: JobConfirmationService
) {
    @PostMapping("/hendelseprosessering/start")
    fun startHendelseProsessering(
        @RequestBody(required = false) options: SkatteHendelsePubliseringOptions =
            SkatteHendelsePubliseringOptions()
    ): ResponseEntity<String> {
        if (!jobConfirmationService.isValid(options.confirmationCode)) {
            val newToken = jobConfirmationService.generateToken()
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(jobConfirmationService.confirmationMessage(newToken))
        }
        log.info("Starter hendelseprosessering. Options: $options")
        if (kafkaContainerMonitor.isKafkaContainerStopped()) {
            return ResponseEntity("Kafka container har stoppet", HttpStatus.SERVICE_UNAVAILABLE)
        }

        skatteHendelsePublisering.asynkronProsesseringAvSkattHendelser(options)
        return ResponseEntity.ok(
            "✅ Hendelseprosessering startet på cluster:${jobConfirmationService.naisClusterName}.\n" +
                    "dryRunPublisering:${jobConfirmationService.dryRunPublisering} Options: $options"
        )
    }

    @PostMapping("/hendelseprosessering/stop")
    fun stopHendelseProsessering(): ResponseEntity<String> {
        log.info("Stopper hendelseprosessering")
        skatteHendelsePublisering.stopProsesseringAvSkattHendelser()
        return ResponseEntity.ok("Hendelseprosessering stoppet")
    }

    @GetMapping("/hendelseprosessering/status")
    fun status(
        @RequestParam(value = "periodeFilter", required = false) periodeFilter: String = "2024",
        @RequestParam(value = "kunIdentMatch", required = false) kunIdentMatch: Boolean = false
    ) =
        ResponseEntity<Map<String, Any?>>(skatteHendelsePublisering.status(periodeFilter, kunIdentMatch), HttpStatus.OK)

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

    fun Periode.toMap(): Map<String, Any?> {
        val pensjonsgivendeInntekterForPeriode = pensjonsgivendeInntektRepository.findByPeriode(this)
        return linkedMapOf(
            "id" to this.id,
            "fom" to this.fom,
            "tom" to this.tom,
            "unikPensjonsgivendeInntekt" to pensjonsgivendeInntekterForPeriode.count(),
            "duplikaterOppdaget" to pensjonsgivendeInntekterForPeriode.sumOf { it.duplikater },
            "publiseringsHistorikk" to this.publiseringsHistorikk.map { it.toMap() }
        )
    }

    fun SekvensHistorikk.toMap(): Map<String, Any?> {
        return linkedMapOf(
            "id" to this.id,
            "sekvensnummer" to this.sekvensnummer,
            "antall" to this.antall,
            "sisteHendelseTid" to this.sisteHendelseTid
        )
    }

    fun PubliseringsHistorikk.toMap(): Map<String, Any?> {
        return linkedMapOf(
            "id" to this.id,
            "sekvensnummer" to this.sekvensnummer,
            "sisteHendelseTid" to this.sisteHendelseTid
        )
    }
}
