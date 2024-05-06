package no.nav.melosysskattehendelser.domain

import jakarta.persistence.*


@Entity
@Table(name = "PERSON")
class Person(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "ident", nullable = false)
    val ident: String


) {
    override fun toString(): String {
        return "Person(id=$id, ident='$ident')"
    }
}


