package no.nav.melosysskattehendelser.domain

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import no.nav.melosysskattehendelser.skatt.PensjonsgivendeInntektResponse

@Converter
class PensjonsgivendeInntektResponseConverter :
    AttributeConverter<PensjonsgivendeInntektResponse, String> {

    private val mapper = jacksonObjectMapper()

    override fun convertToDatabaseColumn(attribute: PensjonsgivendeInntektResponse): String =
        mapper.writeValueAsString(attribute)

    override fun convertToEntityAttribute(dbData: String): PensjonsgivendeInntektResponse {
        return mapper.readValue<PensjonsgivendeInntektResponse>(dbData)
    }
}
