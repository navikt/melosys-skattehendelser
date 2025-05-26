package no.nav.melosysskattehendelser.prosessering

data class SkatteHendelsePubliseringStats(
    @Volatile var totaltAntallHendelser: Int = 0,
    @Volatile var personerFunnet: Int = 0,
    @Volatile var sisteSekvensnummer: Long = 0,
    @Volatile var antallBatcher: Int = 0,
    @Volatile var sisteBatchSize: Int = 0,
    @Volatile var personCacheSize: Int = 0,
    var periodeFilter: String? = "2024",
    var kunIdentMatch: Boolean = false,
) : JobMonitor.Stats {
    fun registerHendelseStats() {
        totaltAntallHendelser++
    }

    override fun reset() {
        totaltAntallHendelser = 0
        personerFunnet = 0
        sisteSekvensnummer = 0
        antallBatcher = 0
        sisteBatchSize = 0
        personCacheSize = 0
    }

    override fun asMap(): Map<String, Any> {
        return mapOf(
            "personCache" to personCacheSize,
            "totaltAntallHendelser" to totaltAntallHendelser,
            "personerFunnet" to personerFunnet,
            "sisteSekvensnummer" to sisteSekvensnummer,
            "antallBatcher" to antallBatcher,
            "sisteBatchSize" to sisteBatchSize,
        )
    }
}

