package no.nav.melosysskattehendelser.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.melosysskattehendelser.domain.ÅrFilter
import no.nav.melosysskattehendelser.domain.SkattepliktigUttrekk
import no.nav.melosysskattehendelser.domain.SkattepliktigUttrekkRepository
import no.nav.security.token.support.core.api.Protected
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val log = KotlinLogging.logger { }

@RestController
@Protected
@RequestMapping("/api/admin/skattepliktige")
@Tag(name = "Skattepliktige", description = "Grunnlag til årsavregningskjøringen i melosys-api")
class SkattepliktigeController(
    private val skattepliktigUttrekkRepository: SkattepliktigUttrekkRepository,
    private val tokenValidationContextHolder: TokenValidationContextHolder,
    @Value("\${skattepliktige.tillatte-klienter}") private val tillatteKlienter: List<String>,
) {
    @GetMapping
    @Operation(
        summary = "Hent personer med publiserte skattehendelser for et år",
        description = "Én rad per person, klar som input til årsavregningskjøringen i melosys-api."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Personer hentet"),
            ApiResponse(responseCode = "400", description = "Ugyldig år, årsfilter eller tidspunkt"),
            ApiResponse(responseCode = "401", description = "Mangler gyldig token"),
            ApiResponse(responseCode = "403", description = "Klienten har ikke tilgang")
        ]
    )
    fun hentSkattepliktige(
        @Parameter(description = "Året uttrekket gjelder, for eksempel 2025")
        @RequestParam("gjelderAar") gjelderÅr: Int,
        @Parameter(description = "FOM_AAR: året perioden starter i (standard). INNTEKTSAAR: inntektsåret publiseringen gjaldt.")
        @RequestParam("aarFilter", defaultValue = "FOM_AAR") årFilter: ÅrFilter,
        @Parameter(description = "Ta bare med personer med siste publisering etter dette tidspunktet (norsk tid, uten tidssone), for eksempel 2026-09-08T00:00:00")
        @RequestParam("publisertEtter", required = false) publisertEtterTekst: String?,
    ): ResponseEntity<SkattepliktigeRespons> {
        val klient = klient()
        if (klient !in tillatteKlienter) {
            log.warn { "Uttrekk av skattepliktige avvist for klient=$klient" }
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Klienten har ikke tilgang")
        }
        val publisertEtter = publisertEtterTekst?.let(::tilNorskTid)
        val skattepliktige = skattepliktigUttrekkRepository.hentPubliserte(gjelderÅr, årFilter, publisertEtter)
        log.info {
            "Uttrekk av skattepliktige: klient=$klient, gjelderÅr=$gjelderÅr, årFilter=$årFilter, " +
                "publisertEtter=$publisertEtter, antall=${skattepliktige.size}"
        }
        return ResponseEntity.ok(
            SkattepliktigeRespons(gjelderÅr, årFilter, publisertEtter, skattepliktige.size, skattepliktige)
        )
    }

    private fun klient(): String? =
        tokenValidationContextHolder.getTokenValidationContext().getClaims("aad").getStringClaim("azp_name")

    // ISO_LOCAL_DATE_TIME avviser tidssone; @DateTimeFormat(ISO.DATE_TIME) forkaster den stille.
    private fun tilNorskTid(tekst: String): LocalDateTime =
        try {
            LocalDateTime.parse(tekst, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (e: DateTimeParseException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "publisertEtter må være norsk tid uten tidssone: $tekst")
        }
}

data class SkattepliktigeRespons(
    val gjelderAar: Int,
    val aarFilter: ÅrFilter,
    val publisertEtter: LocalDateTime?,
    val antall: Int,
    val skattepliktige: List<SkattepliktigUttrekk>,
)
