package no.nav.melosysskattehendelser.prosessering

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class SkatteHendelsePubliseringOptions(
    val useCache: Boolean = false,
    val confirmationCode: String? = null,
    @field:Schema(
        description = "Les skatteoppgjør på nytt fra sekvensnummeret Sigrun gir for datoen, fram til lagret sekvensnummer. " +
                "Lagret sekvensnummer endres ikke. Kan ikke brukes sammen med fraSekvensnummer."
    )
    val fraDato: LocalDate? = null,
    @field:Schema(
        description = "Fortsett en omlesing fra dette sekvensnummeret, fram til lagret sekvensnummer. " +
                "Kan ikke brukes sammen med fraDato."
    )
    val fraSekvensnummer: Long? = null,
) {
    fun erOmlesing() = fraDato != null || fraSekvensnummer != null

    fun valideringsfeil(): String? = when {
        fraDato != null && fraSekvensnummer != null -> "fraDato og fraSekvensnummer kan ikke brukes sammen"
        fraSekvensnummer != null && fraSekvensnummer <= 0 -> "fraSekvensnummer må være større enn 0"
        else -> null
    }
}
