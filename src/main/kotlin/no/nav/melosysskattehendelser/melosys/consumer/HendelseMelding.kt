package no.nav.melosysskattehendelser.melosys.consumer

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import tools.jackson.databind.JsonNode
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
) : HendelseMelding()

data class Periode(
    val fom: LocalDate?,
    val tom: LocalDate?,
    val innvilgelsesResultat: InnvilgelsesResultat
) {
    fun erGyldig(): Boolean {
        return fom != null && tom != null && innvilgelsesResultat == InnvilgelsesResultat.INNVILGET
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

// melosys-api sender Sakstyper og Sakstemaer som {"kode": "...", "term": "..."} siden Spring Boot 4
// (melosys-api #3272), og som tekst før det. InnvilgelsesResultat sendes fortsatt som tekst.
private fun JsonNode.kode(): String = if (isObject) path("kode").asString() else asString()

enum class Sakstyper {
    EU_EOS,
    TRYGDEAVTALE,
    FTRL;

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun fra(verdi: JsonNode): Sakstyper = valueOf(verdi.kode())
    }
}

enum class Sakstemaer {
    MEDLEMSKAP_LOVVALG,
    UNNTAK,
    TRYGDEAVGIFT;

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun fra(verdi: JsonNode): Sakstemaer = valueOf(verdi.kode())
    }
}

enum class InnvilgelsesResultat() {
    INNVILGET,
    DELVIS_INNVILGET,
    AVSLAATT,
    OPPHØRT
}
