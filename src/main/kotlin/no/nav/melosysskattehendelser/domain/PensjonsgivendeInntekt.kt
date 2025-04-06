package no.nav.melosysskattehendelser.domain

import jakarta.persistence.*
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektResponse
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "pensjonsgivende_inntekt")
class PensjonsgivendeInntekt(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(optional = false)
    @JoinColumn(name = "periode_id", nullable = false)
    val periode: Periode,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "historisk_inntekt", columnDefinition = "jsonb", nullable = false)
    @Convert(converter = PensjonsgivendeInntektResponseConverter::class)
    val historiskInntekt: PensjonsgivendeInntektResponse
)
