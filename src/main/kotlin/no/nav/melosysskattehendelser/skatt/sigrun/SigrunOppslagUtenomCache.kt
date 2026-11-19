package no.nav.melosysskattehendelser.skatt.sigrun

import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektRequest
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Oppslag i Sigrun for admin-endepunktene, utenom cachen nattjobben bruker: svaret lagres ikke i cachen,
 * og et svar nattjobben har lagt der for samme person og år fjernes, så nattjobben ikke bruker et eldre svar.
 */
@Component
class SigrunOppslagUtenomCache(
    private val consumer: SigrunPensjonsgivendeInntektConsumer,
    private val cacheManager: CacheManager,
    // 10 oppslag × 10 s er 100 s, under de 2 minuttene melosys-api venter på personoppslaget
    @Value("\${sigrun.tidsfrist-utenom-cache:10s}") private val tidsfrist: Duration,
) {
    fun hentUtenomCache(ident: String, inntektsaar: String): PensjonsgivendeInntektResponse {
        val request = PensjonsgivendeInntektRequest(inntektsaar = inntektsaar, navPersonident = ident)
        cacheManager.getCache(PENSJONSGIVENDE_INNTEKT_CACHE)?.evict(SigrunPensjonsgivendeInntektConsumer.cachenokkel(request))
        return consumer.hentUtenCache(request, tidsfrist)
    }
}
