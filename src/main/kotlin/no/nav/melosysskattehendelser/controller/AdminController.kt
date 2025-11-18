package no.nav.melosysskattehendelser.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
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
import no.nav.security.token.support.core.api.Protected
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val log = KotlinLogging.logger { }

@RestController
@Protected
@RequestMapping("/admin")
@Tag(name = "Admin", description = "Administrative endepunkter for administrasjon av skattehendelser og Kafka-meldinger")
class AdminController(
    private val skatteHendelsePublisering: SkatteHendelsePublisering,
    private val personRepository: PersonRepository,
    private val pensjonsgivendeInntektRepository: PensjonsgivendeInntektRepository,
    private val kafkaContainerMonitor: KafkaContainerMonitor,
    private val skattehendelserProducer: SkattehendelserProducer,
    private val jobConfirmationService: JobConfirmationService
) {
    @PostMapping("/hendelseprosessering/start")
    @Operation(
        summary = "Start hendelseprosessering",
        description = "Starter asynkron prosessering av skattehendelser fra Sigrun API. Krever bekreftelseskode for sikkerhet."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Hendelseprosessering startet"),
            ApiResponse(responseCode = "400", description = "Ugyldig bekreftelseskode"),
            ApiResponse(responseCode = "503", description = "Kafka container har stoppet")
        ]
    )
    fun startHendelseProsessering(
        @Parameter(description = "Opsjoner for hendelseprosessering inkludert bekreftelseskode")
        @RequestBody(required = false) options: SkatteHendelsePubliseringOptions =
            SkatteHendelsePubliseringOptions()
    ): ResponseEntity<String> {
        if (!jobConfirmationService.isValid(options.confirmationCode)) {
            val newToken = jobConfirmationService.generateToken()
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(jobConfirmationService.confirmationMessage(newToken))
        }
        log.info { "Starter hendelseprosessering. Options: $options" }
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
    @Operation(
        summary = "Stopp hendelseprosessering",
        description = "Stopper pågående prosessering av skattehendelser."
    )
    @ApiResponse(responseCode = "200", description = "Hendelseprosessering stoppet")
    fun stopHendelseProsessering(): ResponseEntity<String> {
        log.info { "Stopper hendelseprosessering" }
        skatteHendelsePublisering.stopProsesseringAvSkattHendelser()
        return ResponseEntity.ok("Hendelseprosessering stoppet")
    }

    @GetMapping("/hendelseprosessering/status")
    @Operation(
        summary = "Hent status for hendelseprosessering",
        description = "Henter status for pågående eller tidligere hendelseprosessering med mulighet for filtrering."
    )
    @ApiResponse(responseCode = "200", description = "Status hentet")
    fun status(
        @Parameter(description = "Filter for periode (standard: 2024)")
        @RequestParam(value = "periodeFilter", required = false) periodeFilter: String = "2024",
        @Parameter(description = "Vis kun personer med ident match (standard: false)")
        @RequestParam(value = "kunIdentMatch", required = false) kunIdentMatch: Boolean = false
    ) =
        ResponseEntity<Map<String, Any?>>(skatteHendelsePublisering.status(periodeFilter, kunIdentMatch), HttpStatus.OK)

    @GetMapping("/person/{id}")
    @Operation(
        summary = "Hent person etter ID",
        description = "Henter detaljert informasjon om en person inkludert perioder, inntekter og historikk."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Person funnet"),
            ApiResponse(responseCode = "404", description = "Person ikke funnet")
        ]
    )
    fun getPerson(
        @Parameter(description = "Intern person-ID")
        @PathVariable id: Long
    ): ResponseEntity<Map<String, Any?>> {
        val person = personRepository.findPersonById(id) ?: return ResponseEntity(HttpStatus.NOT_FOUND)
        return ResponseEntity(person.toMap(), HttpStatus.OK)
    }

    @GetMapping("/person")
    @Operation(
        summary = "Hent liste over personer",
        description = "Henter en liste over de siste registrerte personene i systemet."
    )
    @ApiResponse(responseCode = "200", description = "Liste over personer")
    fun getPersoner(
        @Parameter(description = "Maksimalt antall personer å hente (standard: 10)")
        @RequestParam(defaultValue = "10") max: Int
    ): ResponseEntity<List<Map<String, Any?>>> {
        val list: List<Map<String, Any?>> = personRepository.findAll()
            .sortedByDescending { it.id }
            .take(max)
            .map { it.toMap() }
        return ResponseEntity(list, HttpStatus.OK)
    }

    @PostMapping("/kafka")
    @Operation(
        summary = "Publiser Kafka-melding",
        description = "Publiserer en Melosys skattehendelse direkte til Kafka for testing."
    )
    @ApiResponse(responseCode = "200", description = "Melding publisert")
    fun lagKafkaMelding(
        @Parameter(description = "Melosys skattehendelse å publisere")
        @RequestBody melosysSkatteHendelse: MelosysSkatteHendelse
    ): ResponseEntity<String> {
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
