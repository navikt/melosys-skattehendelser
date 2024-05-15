package no.nav.melosysskattehendelser.skatt

import no.nav.melosysskattehendelser.sigrun.Hendelse
import no.nav.melosysskattehendelser.sigrun.HendelseRequest

fun interface SkattHendelseConsumer {
    fun hentHendelseListe(request: HendelseRequest) : List<Hendelse>
}