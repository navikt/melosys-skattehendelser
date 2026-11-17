package no.nav.melosysskattehendelser

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.melosysskattehendelser.controller.IdentForOppslag
import no.nav.melosysskattehendelser.controller.PersonoppslagForespørsel
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntekt
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektRequest
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektResponse
import no.nav.melosysskattehendelser.skatt.sigrun.PensjonsgivendeInntektForFolketrygdenRequest
import org.junit.jupiter.api.Test

/** Spring, WebClient og log.debug("$objekt") skriver toString() i loggen. */
class IdentMaskertIToStringTest {

    @Test
    fun `IdentForOppslag viser ikke identen`() {
        IdentForOppslag(FNR, gjeldende = true).toString().let {
            it.skalIkkeVise(FNR)
            it shouldContain "gjeldende=true"
        }
    }

    @Test
    fun `PersonoppslagForespørsel viser antall identer, ikke identene`() {
        PersonoppslagForespørsel(2025, listOf(IdentForOppslag(FNR, true), IdentForOppslag(DNR, false))).toString().let {
            it shouldBe "PersonoppslagForespørsel(inntektsaar=2025, antallIdenter=2)"
            it.skalIkkeVise(FNR)
            it.skalIkkeVise(DNR)
        }
    }

    @Test
    fun `PensjonsgivendeInntektRequest viser ikke identen`() {
        PensjonsgivendeInntektRequest(inntektsaar = "2025", navPersonident = FNR).toString().let {
            it.skalIkkeVise(FNR)
            it shouldContain "inntektsaar=2025"
        }
    }

    @Test
    fun `PensjonsgivendeInntektForFolketrygdenRequest viser ikke identen`() {
        PensjonsgivendeInntektForFolketrygdenRequest(personident = FNR, inntektsaar = "2025").toString().let {
            it.skalIkkeVise(FNR)
            it shouldContain "inntektsaar=2025"
        }
    }

    @Test
    fun `PensjonsgivendeInntektResponse viser ikke norskPersonidentifikator`() {
        val inntekt = PensjonsgivendeInntekt("FASTLAND", "2026-05-01", "500000", null, null, null)
        PensjonsgivendeInntektResponse(
            slettet = false,
            norskPersonidentifikator = FNR,
            inntektsaar = "2025",
            pensjonsgivendeInntekt = listOf(inntekt),
        ).toString().let {
            it.skalIkkeVise(FNR)
            it shouldContain "slettet=false"
            it shouldContain "inntektsaar=2025"
            it shouldContain "pensjonsgivendeInntekt=[$inntekt]"
        }
    }

    // Ingen fire sifre på rad fra identen, så heller ikke en del av den (f.eks. fødselsdatoen)
    private fun String.skalIkkeVise(ident: String) =
        ident.windowed(4).forEach { this shouldNotContain it }

    private companion object {
        const val FNR = "12345678901"
        const val DNR = "41234567890"
    }
}
