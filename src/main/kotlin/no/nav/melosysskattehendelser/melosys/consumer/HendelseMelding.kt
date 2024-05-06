package no.nav.melosysskattehendelser.melosys.consumer

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.melosysskattehendelser.domain.Person

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = UkjentMelding::class)
@JsonSubTypes(
    JsonSubTypes.Type(value = HendelseMelding::class, name = "HendelseMelding"),
    JsonSubTypes.Type(value = VedtakHendelseMelding::class, name = "VedtakHendelseMelding")
)
open class HendelseMelding

data class VedtakHendelseMelding(
    val folkeregisterIdent: String,
    val sakstype: Sakstyper,
    val sakstema: Sakstemaer
) : HendelseMelding(){

    fun toPerson():Person{
        return Person(ident = folkeregisterIdent)
    }
}

data class UkjentMelding(
    val properties: MutableMap<String, Any> = mutableMapOf()
) : HendelseMelding() {

    @JsonAnySetter
    fun setAdditionalProperty(name: String, value: Any) {
        properties[name] = value
    }
}

enum class Sakstyper{
    EO_EOS,
    TRYGDEAVTALE,
    FTRL
}

enum class Sakstemaer{
    MEDLEMSKAP_LOVVALG,
    UNNTAK,
    TRYGDEAVGIFT
}