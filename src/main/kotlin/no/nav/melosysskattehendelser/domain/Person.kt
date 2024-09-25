package no.nav.melosysskattehendelser.domain

import jakarta.persistence.*


@Entity
@Table(name = "PERSON")
class Person(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "ident", nullable = false)
    val ident: String,

    @OneToMany(mappedBy = "person", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val perioder: MutableList<Periode> = mutableListOf(),

    @OneToMany(mappedBy = "person", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val sekvensHistorikk: MutableList<SekvensHistorikk> = mutableListOf()
) {
    override fun toString(): String {
        return "Person(id=$id, ident='$ident', sekvensnummer=$sekvensHistorikk, perioder=$perioder)"
    }

    fun hendelse(sekvensnummer: Long): SekvensHistorikk {
        sekvensHistorikk.find { it.sekvensnummer == sekvensnummer }?.let { sekvensHistorie ->
            sekvensHistorie.antall++
            sekvensHistorie.sisteHendelseTid = java.sql.Timestamp(System.currentTimeMillis())
            return sekvensHistorie
        }

        return SekvensHistorikk(
            sekvensnummer = sekvensnummer,
            person = this
        ).also {
            sekvensHistorikk.add(it)
        }
    }

    fun harTreffIPeriode(year: Int): Boolean =
        perioder.any { it.harTreff(year) }
}
