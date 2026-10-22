package no.nav.melosysskattehendelser.prosessering

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SkatteHendelsePubliseringStatsTest {

    @Test
    fun `reset nullstiller alle tellere og fremdriftsfelt fra forrige kjøring`() {
        val stats = SkatteHendelsePubliseringStats(
            totaltAntallHendelser = 1,
            personerFunnet = 1,
            startSekvensnummer = 1,
            sisteSekvensnummer = 1,
            sisteLesteSekvensnummer = 1,
            markør = 1,
            førsteRegistreringstidspunkt = "2025-06-01T10:00:00",
            sisteRegistreringstidspunkt = "2025-06-01T10:00:00",
            antallBatcher = 1,
            sisteBatchSize = 1,
            personCacheSize = 1,
            publisert = 1,
            duplikater = 1,
            periodeFilter = "2023",
            kunIdentMatch = true,
        )


        stats.reset()


        stats shouldBe SkatteHendelsePubliseringStats(periodeFilter = "2023", kunIdentMatch = true)
    }
}
