package no.nav.melosysskattehendelser.domain

import jakarta.persistence.*


@Entity
@Table(name = "PERSON")
class Person(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "ident", nullable = false)
    var ident: String? = null
)
