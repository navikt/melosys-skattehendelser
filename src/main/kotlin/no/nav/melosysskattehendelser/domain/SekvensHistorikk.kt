package no.nav.melosysskattehendelser.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "SEKVENS_HISTORIKK")
class SekvensHistorikk(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    val person: Person,

    @Column(name = "sekvensnummer", nullable = false)
    val sekvensnummer: Long,

    @Column(name = "antall", nullable = false)
    var antall: Int = 0,

    @Column(name = "siste_hendelse_tid", nullable = false)
    var sisteHendelseTid: LocalDateTime = LocalDateTime.now()
) {
    override fun toString(): String {
        return "SekvensHistorikk(id=$id, person.id=${person.id} sekvensnummer=$sekvensnummer, antall=$antall, sisteHendelseTid=$sisteHendelseTid)"
    }

    fun erNyHendelse() = antall == 0
}
