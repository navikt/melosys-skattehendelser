package no.nav.melosysskattehendelser.domain

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

/** Årene en person har lagret pensjonsgivende inntekt for, delt på om Sigrun svarte 404 (slettet). */
data class LagredeInntektsaar(
    val medInntekt: List<String>,
    val slettet: List<String>,
)

@Repository
class LagretInntektsaarRepository(private val jdbcTemplate: NamedParameterJdbcTemplate) {

    /**
     * Leser bare `inntektsaar` og `slettet` ut av `historisk_inntekt`. JSON-en har også
     * `norskPersonidentifikator`, som ikke skal ut av databasen her.
     */
    fun hentForPersoner(personIder: Collection<Long>): Map<Long, LagredeInntektsaar> {
        if (personIder.isEmpty()) return emptyMap()
        val sql = """
            SELECT DISTINCT pe.person_id                                              AS person_id,
                            pi.historisk_inntekt ->> 'inntektsaar'                    AS inntektsaar,
                            COALESCE(CAST(pi.historisk_inntekt ->> 'slettet' AS BOOLEAN), FALSE) AS slettet
            FROM pensjonsgivende_inntekt pi
                     JOIN periode pe ON pe.id = pi.periode_id
            WHERE pe.person_id IN (:personIder)
              AND pi.historisk_inntekt ->> 'inntektsaar' IS NOT NULL
        """.trimIndent()

        val rader = jdbcTemplate.query(sql, MapSqlParameterSource("personIder", personIder)) { rs, _ ->
            Triple(rs.getLong("person_id"), rs.getString("inntektsaar"), rs.getBoolean("slettet"))
        }
        return rader.groupBy({ it.first }) { it.second to it.third }.mapValues { (_, år) ->
            LagredeInntektsaar(
                medInntekt = år.filterNot { it.second }.map { it.first }.distinct().sorted(),
                slettet = år.filter { it.second }.map { it.first }.distinct().sorted(),
            )
        }
    }
}
