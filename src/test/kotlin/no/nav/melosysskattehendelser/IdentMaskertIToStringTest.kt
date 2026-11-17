package no.nav.melosysskattehendelser

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.melosysskattehendelser.controller.IdentForOppslag
import no.nav.melosysskattehendelser.controller.PersonoppslagForespørsel
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektRequest
import no.nav.melosysskattehendelser.skatt.sigrun.PensjonsgivendeInntektForFolketrygdenRequest
import org.junit.jupiter.api.Test

/** Spring, WebClient og log.debug("$objekt") skriver toString() i loggen. */
class IdentMaskertIToStringTest {

    @Test
    fun `IdentForOppslag viser ikke identen`() {
        IdentForOppslag(FNR, gjeldende = true).toString().let {
            ELLEVE_SIFRE.find(it)?.value.shouldBeNull()
            it shouldContain "gjeldende=true"
        }
    }

    @Test
    fun `PersonoppslagForespørsel viser antall identer, ikke identene`() {
        PersonoppslagForespørsel(2025, listOf(IdentForOppslag(FNR, true), IdentForOppslag(DNR, false))).toString() shouldBe
            "PersonoppslagForespørsel(inntektsaar=2025, antallIdenter=2)"
    }

    @Test
    fun `PensjonsgivendeInntektRequest viser ikke identen`() {
        PensjonsgivendeInntektRequest(inntektsaar = "2025", navPersonident = FNR).toString().let {
            ELLEVE_SIFRE.find(it)?.value.shouldBeNull()
            it shouldContain "inntektsaar=2025"
        }
    }

    @Test
    fun `PensjonsgivendeInntektForFolketrygdenRequest viser ikke identen`() {
        PensjonsgivendeInntektForFolketrygdenRequest(personident = FNR, inntektsaar = "2025").toString().let {
            ELLEVE_SIFRE.find(it)?.value.shouldBeNull()
            it shouldContain "inntektsaar=2025"
        }
    }

    private companion object {
        const val FNR = "12345678901"
        const val DNR = "41234567890"
        val ELLEVE_SIFRE = Regex("""\d{11}""")
    }
}
