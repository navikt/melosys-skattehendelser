package no.nav.melosysskattehendelser.domain

import org.springframework.data.repository.CrudRepository

interface PensjonsgivendeInntektRepository : CrudRepository<PensjonsgivendeInntekt, Long> {
    fun findByPeriode(periode: Periode): List<PensjonsgivendeInntekt>
}