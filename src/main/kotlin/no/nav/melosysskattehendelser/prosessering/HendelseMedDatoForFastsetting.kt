package no.nav.melosysskattehendelser.prosessering

import no.nav.melosysskattehendelser.skatt.Hendelse

data class HendelseMedDatoForFastsetting(
    val hendelse: Hendelse,
    val datoForFastsetting: List<String>
)
