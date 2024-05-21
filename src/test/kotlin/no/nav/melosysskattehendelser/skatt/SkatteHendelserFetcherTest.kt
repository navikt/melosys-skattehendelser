package no.nav.melosysskattehendelser.skatt

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SkatteHendelserFetcherTest {
    @Test
    fun `batch-size 5 og startSeksvensnummer=1 og stopAt=14`() {
        val batchSize = 5
        val skatteHendelserFetcher = SkatteHendelserFetcher(skatteHendelserConsumer(batchSize, 14), batchSize)
        val batchDoneCallbacks = mutableListOf<Long>()


        val result = skatteHendelserFetcher.hentHendelser(
            startSeksvensnummer = 1,
            batchDone = { batchDoneCallbacks.add(it) }
        ).toList()


        result
            .shouldHaveSize(13)
            .map { it.sekvensnummer }.shouldBe((1L..13L).toList())

        batchDoneCallbacks
            .shouldHaveSize(3)
            .shouldBe(listOf(6, 11, 14))
    }

    @Test
    fun `batch-size 10 og startSeksvensnummer=100 og stopAt=124`() {
        val batchSize = 10
        val skatteHendelserFetcher = SkatteHendelserFetcher(skatteHendelserConsumer(batchSize, 124), batchSize)
        val batchDoneCallbacks = mutableListOf<Long>()


        val result = skatteHendelserFetcher.hentHendelser(
            startSeksvensnummer = 100,
            batchDone = { batchDoneCallbacks.add(it) }
        ).toList()


        result
            .shouldHaveSize(24)
            .map { it.sekvensnummer }.shouldBe((100L..123).toList())

        batchDoneCallbacks
            .shouldHaveSize(3)
            .shouldBe(listOf(110, 120, 124))

    }

    private fun skatteHendelserConsumer(batchSize: Int = 10, stopAt: Long = 35L) = object : SkatteHendelseConsumer {
        override fun hentHendelseListe(request: HendelseRequest): List<Hendelse> = sequence {
            val ident = 20107012345L
            repeat(batchSize) { i ->
                val sekvensnummer = request.seksvensnummerFra + i
                if (sekvensnummer >= stopAt) return@sequence
                yield(
                    Hendelse(
                        gjelderPeriode = "2023",
                        identifikator = ident.plus(i).toString(),
                        sekvensnummer = sekvensnummer,
                        somAktoerid = false
                    )
                )
            }
        }.toList()

        override fun getConsumerId(): String = "test"
        override fun getStartSekvensnummer(start: LocalDate): Long = 1
    }

}