package no.nav.melosysskattehendelser.domain

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

enum class AarFilter {
    /** Året perioden starter i. Standard. */
    FOM_AAR,

    /** Inntektsåret publiseringen gjaldt. */
    INNTEKTSAAR,
}

data class SkattepliktigUttrekk(
    val gjelderPeriode: String,
    val identifikator: String,
    val sisteHendelseTid: LocalDateTime,
    val inntektsaar: List<String>,
    val antallPubliseringer: Int,
)

@Repository
class SkattepliktigUttrekkRepository(private val jdbcTemplate: NamedParameterJdbcTemplate) {

    fun hentPubliserte(aar: Int, aarFilter: AarFilter, publisertEtter: LocalDateTime?): List<SkattepliktigUttrekk> {
        val aarBetingelse = when (aarFilter) {
            AarFilter.FOM_AAR -> "EXTRACT(YEAR FROM pe.fom) = :aar"
            AarFilter.INNTEKTSAAR -> "ph.inntektsaar = :aarTekst"
        }
        val sql = """
            SELECT p.ident                                                       AS identifikator,
                   MAX(ph.siste_hendelse_tid)                                    AS siste_hendelse_tid,
                   STRING_AGG(DISTINCT ph.inntektsaar, ',' ORDER BY ph.inntektsaar) AS inntektsaar,
                   COUNT(*)                                                      AS antall_publiseringer
            FROM person p
                     JOIN periode pe ON pe.person_id = p.id
                     JOIN publiserings_historikk ph ON ph.periode_id = pe.id
            WHERE $aarBetingelse
            GROUP BY p.ident
            HAVING CAST(:publisertEtter AS TIMESTAMP) IS NULL
                OR MAX(ph.siste_hendelse_tid) > CAST(:publisertEtter AS TIMESTAMP)
            ORDER BY p.ident
        """.trimIndent()

        val parametere = MapSqlParameterSource()
            .addValue("aar", aar)
            .addValue("aarTekst", aar.toString())
            .addValue("publisertEtter", publisertEtter)

        return jdbcTemplate.query(sql, parametere) { rs, _ ->
            SkattepliktigUttrekk(
                gjelderPeriode = aar.toString(),
                identifikator = rs.getString("identifikator"),
                sisteHendelseTid = rs.getTimestamp("siste_hendelse_tid").toLocalDateTime(),
                inntektsaar = rs.getString("inntektsaar")?.split(",").orEmpty(),
                antallPubliseringer = rs.getInt("antall_publiseringer"),
            )
        }
    }
}
