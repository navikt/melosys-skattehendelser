package no.nav.melosysskattehendelser.domain

import jakarta.persistence.*
import java.sql.Timestamp

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

    @Column(name = "siste_Hendelse_tid", nullable = false)
    var sisteHendelseTid: Timestamp = Timestamp(System.currentTimeMillis())
) {
    override fun toString(): String {
        return "SekvensHistorikk(id=$id, person.id=${person.id} sekvensnummer=$sekvensnummer, antall=$antall, sisteHendelseTid=$sisteHendelseTid)"
    }

}