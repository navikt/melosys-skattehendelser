package no.nav.melosysskattehendelser.domain

import jakarta.persistence.*

@Entity
@Table(name = "SKATTE_HENDELSER_SEKVENS")
class SkatteHendelserSekvens(

    @Id
    @Column(name = "consumer_id", nullable = false)
    val consumerId: String = "sigrun-skatteoppgjoer-hendelser",

    @Column(name = "sekvensnummer", nullable = false)
    val sekvensnummer: Long = 0

) {
    override fun toString(): String {
        return "SkatteHendelserStatus(consumerId=$consumerId, sekvensnummer=$sekvensnummer)"
    }
}