package no.nav.melosysskattehendelser.skatt

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import java.time.LocalDate
import java.time.Year


interface SkatteHendelseConsumer {
    fun hentHendelseListe(request: HendelseRequest): List<Hendelse>
    fun getConsumerId(): String
    fun getStartSekvensnummer(start: LocalDate): Long
}

data class HendelseRequest(
    val seksvensnummerFra: Long,
    val antall: Int,
    val brukAktoerId: Boolean
)

data class Hendelse(
    val gjelderPeriode: String,
    val identifikator: String,
    val sekvensnummer: Long,
    val somAktoerid: Boolean,
    @JsonSetter(nulls = Nulls.SKIP)
    val hendelsetype: String = "ukjent",
    val registreringstidspunkt: String? = null
) {
    fun gjelderPeriodeSomÅr() = Year.parse(gjelderPeriode).value
    fun registreringstidspunktAsYearMonth() = registreringstidspunkt?.substring(0,7) ?: "null"
}
