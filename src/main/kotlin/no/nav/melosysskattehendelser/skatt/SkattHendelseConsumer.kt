package no.nav.melosysskattehendelser.skatt


fun interface SkattHendelseConsumer {
    fun hentHendelseListe(request: HendelseRequest) : List<Hendelse>
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
    val somAktoerid: Boolean
)
