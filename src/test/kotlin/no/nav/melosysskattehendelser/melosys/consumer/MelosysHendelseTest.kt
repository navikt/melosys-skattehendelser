package no.nav.melosysskattehendelser.melosys.consumer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.LocalDate


class MelosysHendelseTest {
    val objectMapper = jacksonObjectMapper().registerModules(JavaTimeModule())

    @Test
    fun `serialize tom hendelse`() {
        val melosysHendelse = MelosysHendelse(HendelseMelding())


        melosysHendelse.toJson() shouldEqualJson """
            {
                "melding": {
                    "type": "HendelseMelding"
                }
            }
            """
    }

    @Test
    fun `serialize VedtakHendelseMelding`() {
        val melosysHendelse = MelosysHendelse(
            VedtakHendelseMelding(
                folkeregisterIdent = "12345",
                sakstype = Sakstyper.TRYGDEAVTALE,
                sakstema = Sakstemaer.TRYGDEAVGIFT,
                periode = Periode(
                    LocalDate.of(2021, 1, 1),
                    LocalDate.of(2022, 1, 1),
                )
            )
        )


        melosysHendelse.toJson() shouldEqualJson """
            {
                "melding": {
                    "type": "VedtakHendelseMelding",
                    "folkeregisterIdent": "12345",
                    "sakstype": "TRYGDEAVTALE",
                    "sakstema": "TRYGDEAVGIFT"
                }
            }"""
    }


    @Test
    fun `deserialize HendelseMelding`() {
        val json = """
            {
                "melding": {
                    "type": "HendelseMelding"
                }
            }"""


        val result = objectMapper.readValue<MelosysHendelse>(json)


        result.melding.shouldBeInstanceOf<HendelseMelding>()
    }

    @Test
    fun `deserialize VedtakHendelseMelding`() {
        val json = """
            {
                "melding": {
                    "type": "VedtakHendelseMelding",
                    "folkeregisterIdent": "12345",
                    "sakstype": "TRYGDEAVTALE",
                    "sakstema": "TRYGDEAVGIFT"
                }
            }"""


        val result = objectMapper.readValue<MelosysHendelse>(json)


        result.melding.shouldBe(
            VedtakHendelseMelding(
                folkeregisterIdent = "12345",
                sakstype = Sakstyper.TRYGDEAVTALE,
                sakstema = Sakstemaer.TRYGDEAVGIFT
            )
        )
    }

    @Test
    fun `deserialize VedtakHendelseMelding med periode`() {
        val json = """
            {
                "melding": {
                    "type": "VedtakHendelseMelding",
                    "folkeregisterIdent": "12345",
                    "sakstype": "TRYGDEAVTALE",
                    "sakstema": "TRYGDEAVGIFT",
                    "periode": {
                          "fom": [2021, 1, 1],
                          "tom": [2022, 1, 1]
                    }
                    
                }
            }"""


        val result = objectMapper.readValue<MelosysHendelse>(json)


        result.melding.shouldBe(
            VedtakHendelseMelding(
                folkeregisterIdent = "12345",
                sakstype = Sakstyper.TRYGDEAVTALE,
                sakstema = Sakstemaer.TRYGDEAVGIFT,
                periode = Periode(
                    LocalDate.of(2021, 1, 1),
                    LocalDate.of(2022, 1, 1)
                )
            )
        )
    }

    @Test
    fun `deserialize og ignorer ekstra felter i VedtakHendelseMelding`() {
        val json = """
            {
                "melding": {
                    "type": "VedtakHendelseMelding",
                    "folkeregisterIdent": "12345",
                    "sakstype": "TRYGDEAVTALE",
                    "sakstema": "TRYGDEAVGIFT",
                    "ekstarfelt": "DUMMY"
                }
            }"""


        val result = objectMapper.readValue<MelosysHendelse>(json)


        result.melding.shouldBe(
            VedtakHendelseMelding(
                folkeregisterIdent = "12345",
                sakstype = Sakstyper.TRYGDEAVTALE,
                sakstema = Sakstemaer.TRYGDEAVGIFT
            )
        )
    }


    @Test
    fun `retuner UkjentMelding når vi ikke har type`() {

        val json = """{
                    "type": "VedtakHendelseMeldingV2",
                     "pnr": "12345"
                }"""

        val result = objectMapper.readValue<HendelseMelding>(json)

        result.shouldBeInstanceOf<UkjentMelding>()
            .properties.shouldBe(mapOf("pnr" to "12345"))
    }

    private fun Any.toJson(): String = objectMapper.valueToTree<JsonNode?>(this).toPrettyString()
}
