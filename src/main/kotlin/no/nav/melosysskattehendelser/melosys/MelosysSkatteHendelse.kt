package no.nav.melosysskattehendelser.melosys

import no.nav.melosysskattehendelser.skatt.Hendelse

data class MelosysSkatteHendelse(
    val gjelderPeriode: String,
    val identifikator: String,
)

fun Hendelse.toMelosysSkatteHendelse() = MelosysSkatteHendelse(gjelderPeriode, identifikator)


