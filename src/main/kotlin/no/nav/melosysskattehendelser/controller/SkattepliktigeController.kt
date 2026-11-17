package no.nav.melosysskattehendelser.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Hidden
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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
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
    private val skattepliktigPersonoppslag: SkattepliktigPersonoppslag,
    private val tokenValidationContextHolder: TokenValidationContextHolder,
    private val objectMapper: ObjectMapper,
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
        val klient = krevTillattKlient("Uttrekk av skattepliktige")
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

    /**
     * Fødselsnummeret står i body, så det ikke havner i access-loggen. Skjult i API-dokumentasjonen,
     * så melosys-console ikke tilbyr det i endepunktkjøreren og lagrer et innskrevet fødselsnummer i historikken.
     * Svaret og loggen har aldri ident.
     */
    @Hidden
    @PostMapping("/personoppslag")
    fun personoppslag(@RequestBody(required = false) body: ByteArray?): ResponseEntity<List<PersonoppslagRad>> {
        val klient = krevTillattKlient("Personoppslag")
        // Leses først etter klientsjekken, så en klient uten tilgang får 403 også når body er ugyldig
        val forespørsel = lesForespørsel(body)
        // Sjekkes for hånd: @Size får Spring til å logge hele lista som «rejected value»
        if (forespørsel.identer.size !in 1..MAKS_IDENTER) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "identer må ha 1 til $MAKS_IDENTER identer")
        }
        if (forespørsel.inntektsaar !in 1000..9999) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "inntektsaar må være et årstall")
        }
        val rader = skattepliktigPersonoppslag.slåOpp(forespørsel.inntektsaar, forespørsel.identer)
        log.info {
            "Personoppslag: klient=$klient, inntektsaar=${forespørsel.inntektsaar}, " +
                "antallIdenter=${forespørsel.identer.size}, personIder=${rader.map { it.personId }}, " +
                "sigrunHarInntektForAaret=${rader.associate { it.personId to it.sigrunHarInntektForAaret }}"
        }
        return ResponseEntity.ok(rader)
    }

    private fun lesForespørsel(body: ByteArray?): PersonoppslagForespørsel {
        val forespørsel = try {
            body?.let { objectMapper.readValue(it, PersonoppslagForespørsel::class.java) }
        } catch (e: JacksonException) {
            // Bare typen: Jackson tar med verdien i meldingen, for eksempel en ident
            log.warn { "Ugyldig body i kall til skattepliktige: ${e.javaClass.simpleName}" }
            null
        }
        return forespørsel ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ugyldig body")
    }

    private fun krevTillattKlient(handling: String): String? {
        val klient = klient()
        if (klient !in tillatteKlienter) {
            log.warn { "$handling avvist for klient=$klient" }
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Klienten har ikke tilgang")
        }
        return klient
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

    private companion object {
        const val MAKS_IDENTER = 10
    }
}

data class SkattepliktigeRespons(
    val gjelderAar: Int,
    val aarFilter: ÅrFilter,
    val publisertEtter: LocalDateTime?,
    val antall: Int,
    val skattepliktige: List<SkattepliktigUttrekk>,
)
