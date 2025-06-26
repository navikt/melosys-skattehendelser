package no.nav.melosysskattehendelser.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "PUBLISERINGS_HISTORIKK")
class PubliseringsHistorikk(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "periode_id", nullable = false)
    val periode: Periode,

    @Column(name = "sekvensnummer", nullable = false)
    val sekvensnummer: Long,

    @Column(name = "siste_hendelse_tid", nullable = false)
    var sisteHendelseTid: LocalDateTime = LocalDateTime.now()
) {

    override fun toString(): String {
        return "PeriodeHistorikk(id=$id, periode.id=${periode.id}, sisteHendelseTid=$sisteHendelseTid)"
    }
}
