package no.nav.melosysskattehendelser.melosys.consumer

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.melosysskattehendelser.domain.Person
import java.time.LocalDate

data class MelosysHendelse(
    val melding: HendelseMelding
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = UkjentMelding::class)
@JsonSubTypes(
    JsonSubTypes.Type(value = HendelseMelding::class, name = "HendelseMelding"),
    JsonSubTypes.Type(value = VedtakHendelseMelding::class, name = "VedtakHendelseMelding")
)
@JsonIgnoreProperties(ignoreUnknown = true)
open class HendelseMelding

data class VedtakHendelseMelding(
    val folkeregisterIdent: String,
    val sakstype: Sakstyper,
    val sakstema: Sakstemaer,
    val medlemskapsperioder: List<Periode>,

) : HendelseMelding() {
    fun toPerson() = Person(
        ident = folkeregisterIdent,
    ).also { person ->
        person.perioder.addAll(
            medlemskapsperioder
                .filter { it.innvilgelsesResultat == InnvilgelsesResultat.INNVILGET }
                .filterNot { it.fom == null && it.tom == null }
                .map { periode -> periode.toDbPeriode(person) }
        )
    }
}

data class Periode(
    val fom: LocalDate?,
    val tom: LocalDate?,
    val innvilgelsesResultat: InnvilgelsesResultat
) {
    fun toDbPeriode(person: Person) = no.nav.melosysskattehendelser.domain.Periode(
        fom = fom ?: throw IllegalArgumentException("fom kan ikke være null"),
        tom = tom ?: throw IllegalArgumentException("tom kan ikke være null"),
        person = person
    )
}

data class UkjentMelding(
    val properties: MutableMap<String, Any> = mutableMapOf()
) : HendelseMelding() {

    @JsonAnySetter
    fun setAdditionalProperty(name: String, value: Any) {
        properties[name] = value
    }
}

enum class Sakstyper {
    EU_EOS,
    TRYGDEAVTALE,
    FTRL
}

enum class Sakstemaer {
    MEDLEMSKAP_LOVVALG,
    UNNTAK,
    TRYGDEAVGIFT
}

enum class InnvilgelsesResultat() {
    INNVILGET,
    DELVIS_INNVILGET,
    AVSLAATT,
    OPPHØRT
}
