package no.nav.melosysskattehendelser.domain

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "PERIODE")
class Periode(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne
    @JoinColumn(name = "person_id", nullable = false)
    val person: Person,

    @Column(name = "fom", nullable = false)
    val fom: LocalDate,

    @Column(name = "tom", nullable = false)
    val tom: LocalDate
) {
    override fun toString(): String {
        return "Periode(id=$id, fom=$fom, tom=$tom)"
    }
}