package no.nav.melosysskattehendelser.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.melosysskattehendelser.melosys.consumer.HoppOverKafkaFeilHandler
import no.nav.melosysskattehendelser.melosys.consumer.KafkaContainerMonitor
import no.nav.security.token.support.core.api.Protected
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val log = KotlinLogging.logger { }

@RestController
@Protected
@RequestMapping("/admin/kafka/errors")
@Tag(name = "Kafka-feil", description = "Meldinger som har stoppet lytteren for vedtaksmeldinger")
class KafkaErrorController(
    private val hoppOverKafkaFeilHandler: HoppOverKafkaFeilHandler,
    private val kafkaContainerMonitor: KafkaContainerMonitor,
    @Value("\${HOSTNAME:localhost}") private val podnavn: String
) {
    @GetMapping
    @Operation(
        summary = "Vis meldinger som har stoppet lytteren",
        description = "Viser topic, partisjon, offset, tidspunkt og feilkjeden, ikke innholdet i meldingen. " +
                "Feilene ligger i minnet i poden og forsvinner ved omstart."
    )
    @ApiResponse(responseCode = "200", description = "Feilene på denne poden")
    fun feil(): ResponseEntity<Map<String, Any>> = ResponseEntity.ok(
        mapOf(
            "podnavn" to podnavn,
            "lytteren" to if (kafkaContainerMonitor.isKafkaContainerRunning()) "kjører" else "stoppet",
            "feil" to hoppOverKafkaFeilHandler.feiledeMeldinger()
        )
    )

    @DeleteMapping("/{key}")
    @Operation(
        summary = "Hopp over en melding",
        description = "Merker meldingen og starter lytteren, som leser meldingen på nytt. Feiler den igjen, " +
                "committer lytteren offset + 1 og leser neste. Går den gjennom, er den behandlet som vanlig."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Meldingen hoppes over"),
            ApiResponse(responseCode = "404", description = "Ingen feil med nøkkelen på denne poden"),
            ApiResponse(responseCode = "409", description = "Lytteren har ikke stoppet ennå")
        ]
    )
    fun hoppOver(
        @Parameter(description = "Nøkkelen fra GET, på formen topic-partisjon-offset")
        @PathVariable key: String
    ): ResponseEntity<String> {
        if (!lytterenHarStoppet()) return ikkeStoppet()
        if (!hoppOverKafkaFeilHandler.merkForÅHoppeOver(key)) return ikkeFunnet(key)

        log.warn { "Hopper over melding $key" }
        return ResponseEntity.ok("Meldingen $key hoppes over. ${startLytteren()}")
    }

    @PostMapping("/{key}/retry")
    @Operation(
        summary = "Prøv en melding på nytt",
        description = "Fjerner feilen og starter lytteren, som leser meldingen igjen. Feiler den igjen, stopper lytteren igjen."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Lytteren leser meldingen igjen"),
            ApiResponse(responseCode = "404", description = "Ingen feil med nøkkelen på denne poden"),
            ApiResponse(responseCode = "409", description = "Lytteren har ikke stoppet ennå")
        ]
    )
    fun prøvIgjen(
        @Parameter(description = "Nøkkelen fra GET, på formen topic-partisjon-offset")
        @PathVariable key: String
    ): ResponseEntity<String> {
        if (!lytterenHarStoppet()) return ikkeStoppet()
        if (!hoppOverKafkaFeilHandler.fjern(key)) return ikkeFunnet(key)

        log.info { "Prøver melding $key på nytt" }
        return ResponseEntity.ok("Meldingen $key prøves på nytt. ${startLytteren()}")
    }

    // Feilen vises før lytteren har stoppet. Startes lytteren i det vinduet, stopper den rett etter, og feilen er borte.
    private fun lytterenHarStoppet() =
        !kafkaContainerMonitor.isKafkaContainerRunning() && !kafkaContainerMonitor.isKafkaContainerChildRunning()

    private fun startLytteren() =
        if (kafkaContainerMonitor.startKafkaContainerHvisStoppet()) "Lytteren er startet." else "Lytteren kjørte allerede."

    private fun ikkeStoppet() =
        ResponseEntity.status(HttpStatus.CONFLICT).body("Lytteren har ikke stoppet ennå på pod $podnavn. Prøv igjen om noen sekunder.")

    private fun ikkeFunnet(key: String) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ingen feil med nøkkel $key på pod $podnavn")
}
