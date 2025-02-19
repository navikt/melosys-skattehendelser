package no.nav.melosysskattehendelser.metrics

object MetrikkNavn {
    const val METRIKKER_NAMESPACE = "skattehendelser"

    const val HENDELSE_HENTET = "${METRIKKER_NAMESPACE}.hendelse.hentet"
    const val PERSON_FUNNET = "${METRIKKER_NAMESPACE}.person.funnet"
    const val HENDELSE_PUBLISERT = "${METRIKKER_NAMESPACE}.hendelse.publisert"
    const val DUPLIKAT_HENDELSE = "${METRIKKER_NAMESPACE}.duplikat.hendelse"
    const val PUBLISERING_FEILET = "${METRIKKER_NAMESPACE}.publisering.feilet"
}
