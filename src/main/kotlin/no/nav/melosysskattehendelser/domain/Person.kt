package no.nav.melosysskattehendelser.domain

import jakarta.persistence.*


@Entity
@Table(name = "personer")
class Person(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "ident", nullable = false)
    val ident: String? = null
)
