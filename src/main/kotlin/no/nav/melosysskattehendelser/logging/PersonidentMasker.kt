package no.nav.melosysskattehendelser.logging

import net.logstash.logback.mask.ValueMasker
import tools.jackson.core.TokenStreamContext

/**
 * Prod-masken i logback.xml. Maskerer 11 sifre på rad, også inntil en bokstav eller understrek
 * (`personident_<fnr>`), men bare i `message` og `stack_trace`: en `<valueMask>` treffer alle feltene,
 * også `trace_id` og `span_id` fra OTel-agenten, og rundt 2 % av dem har 11 sifre på rad.
 * Bare 11 sifre: 13 ville treffe millisekundene i Nav-Call-Id (`CallId_<ms>_<n>`).
 */
class PersonidentMasker : ValueMasker {
    override fun mask(context: TokenStreamContext, value: Any?): Any? {
        if (value !is CharSequence || context.parent?.inRoot() != true || context.currentName() !in FELTER) return null
        val tekst = value.toString()
        val maskert = ELLEVE_SIFRE.replace(tekst, "***********")
        return if (maskert == tekst) null else maskert
    }

    private companion object {
        val FELTER = setOf("message", "stack_trace")
        val ELLEVE_SIFRE = Regex("""(?<!\d)\d{11}(?!\d)""")
    }
}
