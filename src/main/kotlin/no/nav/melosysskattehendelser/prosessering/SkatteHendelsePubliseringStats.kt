package no.nav.melosysskattehendelser.prosessering

data class SkatteHendelsePubliseringStats(
    @Volatile var totaltAntallHendelser: Int = 0,
    @Volatile var personerFunnet: Int = 0,
    @Volatile var startSekvensnummer: Long? = null,
    @Volatile var sisteSekvensnummer: Long = 0,
    @Volatile var markør: Long? = null,
    @Volatile var førsteRegistreringstidspunkt: String? = null,
    @Volatile var sisteRegistreringstidspunkt: String? = null,
    @Volatile var antallBatcher: Int = 0,
    @Volatile var sisteBatchSize: Int = 0,
    @Volatile var personCacheSize: Int = 0,
    @Volatile var publisert: Int = 0,
    @Volatile var duplikater: Int = 0,
    var periodeFilter: String? = "2024",
    var kunIdentMatch: Boolean = false,
) : JobMonitor.Stats {
    override fun reset() {
        totaltAntallHendelser = 0
        personerFunnet = 0
        startSekvensnummer = null
        sisteSekvensnummer = 0
        markør = null
        førsteRegistreringstidspunkt = null
        sisteRegistreringstidspunkt = null
        antallBatcher = 0
        sisteBatchSize = 0
        personCacheSize = 0
        publisert = 0
        duplikater = 0
    }

    fun registrerHendelseTid(registreringstidspunkt: String?) {
        if (registreringstidspunkt == null) return
        if (førsteRegistreringstidspunkt == null) førsteRegistreringstidspunkt = registreringstidspunkt
        sisteRegistreringstidspunkt = registreringstidspunkt
    }

    private fun omlesingFremdriftProsent(): Int? {
        val start = startSekvensnummer ?: return null
        val slutt = markør ?: return null
        if (slutt <= start) return 100
        return ((sisteSekvensnummer - start) * 100 / (slutt - start)).toInt().coerceIn(0, 100)
    }

    override fun asMap(): Map<String, Any?> {
        return mapOf(
            "personCache" to personCacheSize,
            "totaltAntallHendelser" to totaltAntallHendelser,
            "personerFunnet" to personerFunnet,
            "startSekvensnummer" to startSekvensnummer,
            "sisteSekvensnummer" to sisteSekvensnummer,
            "markør" to markør,
            "omlesingFremdriftProsent" to omlesingFremdriftProsent(),
            "førsteRegistreringstidspunkt" to førsteRegistreringstidspunkt,
            "sisteRegistreringstidspunkt" to sisteRegistreringstidspunkt,
            "antallBatcher" to antallBatcher,
            "sisteBatchSize" to sisteBatchSize,
            "publisert" to publisert,
            "duplikater" to duplikater,
        )
    }
}
