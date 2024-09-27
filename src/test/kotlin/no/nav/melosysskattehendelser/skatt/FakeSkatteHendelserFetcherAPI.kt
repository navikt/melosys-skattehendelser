package no.nav.melosysskattehendelser.skatt

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.mockito.ArgumentMatchers.anyLong
import java.time.LocalDate

class FakeSkatteHendelserFetcherAPI :
    SkatteHendelserFetcherAPI(skatteHendelseConsumer = mockk(), 10, startDato = LocalDate.now()) {
    val hendelser = mutableListOf<Hendelse>()

    fun reset() = apply {
        hendelser.clear()
    }

    override fun hentHendelser(
        startSeksvensnummer: Long,
        batchDone: (seksvensnummer: Long) -> Unit,
        reportStats: (stats: Stats) -> Unit
    ): Sequence<Hendelse> {
        return hendelser.asSequence()
    }

    override val consumerId: String
        get() = "fake"

    override val startSekvensnummer: Long
        get() = 1L

    fun spyInstance(): SkatteHendelserFetcherAPI {
        val spy = spyk(FakeSkatteHendelserFetcherAPI())

        every { spy.hentHendelser(anyLong(), any()) } returns hendelser.asSequence()

        return spy

    }

}