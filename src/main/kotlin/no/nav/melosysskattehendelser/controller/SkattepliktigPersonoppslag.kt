package no.nav.melosysskattehendelser.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.melosysskattehendelser.domain.LagredeInntektsaar
import no.nav.melosysskattehendelser.domain.LagretInntektsaarRepository
import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektConsumer
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektRequest
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

private val log = KotlinLogging.logger { }

data class PersonoppslagForespørsel(
    val inntektsaar: Int,
    val identer: List<IdentForOppslag>,
)

/** [gjeldende] sier om identen er personens gjeldende ident i PDL. */
data class IdentForOppslag(
    val ident: String,
    val gjeldende: Boolean,
)

/** Én rad per person. Har aldri ident. */
data class PersonoppslagRad(
    val personId: Long,
    val registrertPaaGjeldendeIdent: Boolean,
    /** null når Sigrun ikke ble spurt, eller når oppslaget feilet. false når Sigrun svarte 404. */
    val sigrunHarInntektForAaret: Boolean?,
    val perioder: List<PersonoppslagPeriode>,
    val inntektsaarMedInntekt: List<String>,
    val inntektsaarSlettet: List<String>,
)

data class PersonoppslagPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
    val publiseringer: List<PersonoppslagPublisering>,
)

data class PersonoppslagPublisering(
    val inntektsaar: String?,
    val sisteHendelseTid: LocalDateTime,
)

@Component
class SkattepliktigPersonoppslag(
    private val personRepository: PersonRepository,
    private val lagretInntektsaarRepository: LagretInntektsaarRepository,
    private val pensjonsgivendeInntektConsumer: PensjonsgivendeInntektConsumer,
    private val cacheManager: CacheManager,
) {
    fun slåOpp(inntektsaar: Int, identer: List<IdentForOppslag>): List<PersonoppslagRad> {
        val gjeldendeIdenter = identer.filter { it.gjeldende }.map { it.ident }.toSet()
        val personer = identer.map { it.ident }.distinct()
            .mapNotNull { personRepository.findPersonByIdent(it) }
            .distinctBy { it.id }
        val lagredeÅr = lagretInntektsaarRepository.hentForPersoner(personer.map { it.id })
        val år = inntektsaar.toString()

        return personer.map { person ->
            val lagret = lagredeÅr[person.id] ?: LagredeInntektsaar(emptyList(), emptyList())
            PersonoppslagRad(
                personId = person.id,
                registrertPaaGjeldendeIdent = person.ident in gjeldendeIdenter,
                sigrunHarInntektForAaret = if (skalSpørreSigrun(person, inntektsaar, lagret)) spørSigrun(person, år) else null,
                perioder = person.perioder.sortedBy { it.fom }.map { periode ->
                    PersonoppslagPeriode(
                        fom = periode.fom,
                        tom = periode.tom,
                        publiseringer = periode.publiseringsHistorikk.sortedBy { it.sisteHendelseTid }
                            .map { PersonoppslagPublisering(it.inntektÅr, it.sisteHendelseTid) },
                    )
                },
                inntektsaarMedInntekt = lagret.medInntekt,
                inntektsaarSlettet = lagret.slettet,
            )
        }
    }

    // Sigrun skiller et oppgjør som ikke er publisert, fra ett som ble publisert før personen ble registrert
    private fun skalSpørreSigrun(person: Person, inntektsaar: Int, lagret: LagredeInntektsaar): Boolean {
        val år = inntektsaar.toString()
        val publisert = person.perioder.any { periode -> periode.publiseringsHistorikk.any { it.inntektÅr == år } }
        return person.harTreffIPeriode(inntektsaar) && !publisert && år !in lagret.medInntekt
    }

    // Som GET /admin/person/{id}/pensjonsgivende-inntekt: lagrer og publiserer ingenting.
    // Nattjobben bruker samme cache, så den tømmes før og etter.
    private fun spørSigrun(person: Person, inntektsaar: String): Boolean? {
        val cache = cacheManager.getCache("pensjonsgivendeInntekt")
        val cachenøkkel = "${person.ident}-$inntektsaar"
        cache?.evict(cachenøkkel)
        val harInntekt = try {
            !pensjonsgivendeInntektConsumer.hentPensjonsgivendeInntekt(
                PensjonsgivendeInntektRequest(inntektsaar = inntektsaar, navPersonident = person.ident)
            ).slettet
        } catch (e: Exception) {
            // Bare typen: meldingen er ikke vår og kan inneholde personopplysninger
            log.warn { "Oppslag i Sigrun feilet for person ${person.id}, inntektsår $inntektsaar: ${e.javaClass.simpleName}" }
            null
        }
        cache?.evict(cachenøkkel)
        return harInntekt
    }
}
