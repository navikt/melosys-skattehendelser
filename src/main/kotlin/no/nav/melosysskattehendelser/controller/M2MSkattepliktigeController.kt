package no.nav.melosysskattehendelser.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import no.nav.melosysskattehendelser.domain.AarFilter
import no.nav.melosysskattehendelser.domain.SkattepliktigUttrekk
import no.nav.melosysskattehendelser.domain.SkattepliktigUttrekkRepository
import no.nav.security.token.support.core.api.Protected
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.beans.factory.annotation.Value
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

private val log = KotlinLogging.logger { }

/**
 * Grunnlag til årsavregningskjøringen i melosys-api. Bare klientene i [tillatteKlienter] har tilgang.
 */
@RestController
@Protected
@RequestMapping("/m2m/api/skattepliktige")
class M2MSkattepliktigeController(
    private val skattepliktigUttrekkRepository: SkattepliktigUttrekkRepository,
    private val tokenValidationContextHolder: TokenValidationContextHolder,
    @Value("\${m2m.skattepliktige.tillatte-klienter}") private val tillatteKlienter: List<String>,
) {

    @Operation(
        summary = "Hent personer med publiserte skattehendelser for et år (M2M)",
        description = "Én rad per person, klar som input til årsavregningskjøringen i melosys-api."
    )
    @GetMapping
    fun hentSkattepliktige(
        @Parameter(description = "Året uttrekket gjelder, for eksempel 2025")
        @RequestParam gjelderAar: Int,
        @Parameter(description = "FOM_AAR: året perioden starter i. INNTEKTSAAR: inntektsåret publiseringen gjaldt.")
        @RequestParam(defaultValue = "FOM_AAR") aarFilter: AarFilter,
        @Parameter(description = "Ta bare med personer med siste publisering etter dette tidspunktet")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) publisertEtter: LocalDateTime?,
    ): SkattepliktigeRespons {
        val klient = validerKlient()
        val skattepliktige = skattepliktigUttrekkRepository.hentPubliserte(gjelderAar, aarFilter, publisertEtter)
        log.info {
            "M2M-uttrekk av skattepliktige: klient=$klient, gjelderAar=$gjelderAar, aarFilter=$aarFilter, " +
                "publisertEtter=$publisertEtter, antall=${skattepliktige.size}"
        }
        return SkattepliktigeRespons(gjelderAar, aarFilter, publisertEtter, skattepliktige.size, skattepliktige)
    }

    private fun validerKlient(): String {
        val claims = tokenValidationContextHolder.getTokenValidationContext().getClaims(ISSUER)
        val klient = claims.getStringClaim("azp_name")
        if (claims.getStringClaim("idtyp") != "app" || klient !in tillatteKlienter) {
            log.warn { "Avviste M2M-uttrekk av skattepliktige: klient=$klient, idtyp=${claims.getStringClaim("idtyp")}" }
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Klienten har ikke tilgang")
        }
        return klient
    }

    companion object {
        private const val ISSUER = "aad"
    }
}

data class SkattepliktigeRespons(
    val gjelderAar: Int,
    val aarFilter: AarFilter,
    val publisertEtter: LocalDateTime?,
    val antall: Int,
    val skattepliktige: List<SkattepliktigUttrekk>,
)
