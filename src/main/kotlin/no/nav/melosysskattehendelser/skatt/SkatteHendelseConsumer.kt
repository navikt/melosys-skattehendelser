package no.nav.melosysskattehendelser.skatt

import java.time.LocalDate


interface SkatteHendelseConsumer {
    fun hentHendelseListe(request: HendelseRequest) : List<Hendelse>
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
    val hendelsetype: String,
)
