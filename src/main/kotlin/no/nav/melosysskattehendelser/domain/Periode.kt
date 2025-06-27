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
    val tom: LocalDate,

    @OneToMany(mappedBy = "periode", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val publiseringsHistorikk: MutableList<PubliseringsHistorikk> = mutableListOf()

) {
    override fun toString(): String {
        return "Periode(id=$id, fom=$fom, tom=$tom)"
    }

    fun harTreff(year: Int) = fom.year <= year && tom.year >= year

    fun lagPubliseringsHistorikk(inntektÅr: String, sekvensnummer: Long) = PubliseringsHistorikk(
        periode = this,
        inntektÅr = inntektÅr,
        sekvensnummer = sekvensnummer
    ).also { publiseringsHistorikk.add(it) }
}
