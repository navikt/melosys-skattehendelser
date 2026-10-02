package no.nav.melosysskattehendelser.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

private val log = KotlinLogging.logger { }

@Component
class AdminTilgangInterceptor(
    private val tokenValidationContextHolder: TokenValidationContextHolder,
    @Value("\${admin.driftsgruppe}") private val driftsgruppeId: String,
    @Value("\${admin.console-klient-id}") private val consoleKlientId: String,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val claims = claimsFraGyldigToken()

        if (claims == null) return true   // @Protected svarer 401

        // Gjelder både person- og maskinkall, så klienten sjekkes før idtyp
        val azp = claims.getStringClaim("azp")
        if (azp != consoleKlientId) {
            // azp er en klient-ID, ikke en personopplysning
            log.warn { "Admin-kall avvist: ukjent klient (azp=$azp, ${request.method})" }
            return avvis(response, UKJENT_KLIENT)
        }

        if (erMaskinkall(claims)) return true
        if (erMedlemAvDriftsgruppe(claims)) return true

        log.warn { "Admin-kall avvist: personkall uten driftsgruppe (${request.method})" }
        return avvis(response, MANGLER_DRIFTSGRUPPE)
    }

    // Skrives direkte: Spring Boot tar ikke med meldingen fra ResponseStatusException i svaret
    private fun avvis(response: HttpServletResponse, melding: String): Boolean {
        response.status = 403
        response.contentType = MediaType.TEXT_PLAIN_VALUE
        response.writer.write(melding)
        return false
    }

    private fun claimsFraGyldigToken() =
        tokenValidationContextHolder.getTokenValidationContext().getJwtToken(ISSUER)?.jwtTokenClaims

    // Entra setter idtyp = app bare i maskintoken. Mangler den, regnes kallet som personkall.
    private fun erMaskinkall(claims: JwtTokenClaims) = claims.getStringClaim("idtyp") == IDTYP_MASKIN

    // getAsList gir null når groups mangler
    private fun erMedlemAvDriftsgruppe(claims: JwtTokenClaims) = driftsgruppeId in claims.getAsList("groups").orEmpty()

    companion object {
        const val MANGLER_DRIFTSGRUPPE = "Mangler tilgang til admin-endepunkter"
        const val UKJENT_KLIENT = "Kallet kommer ikke fra en godkjent klient"
        private const val ISSUER = "aad"
        private const val IDTYP_MASKIN = "app"
    }
}
