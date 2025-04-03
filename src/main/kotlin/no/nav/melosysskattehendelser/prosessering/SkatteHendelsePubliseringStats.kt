package no.nav.melosysskattehendelser.prosessering

import no.nav.melosysskattehendelser.skatt.Hendelse
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

data class SkatteHendelsePubliseringStats(
    @Volatile var totaltAntallHendelser: Int = 0,
    @Volatile var personerFunnet: Int = 0,
    @Volatile var sisteSekvensnummer: Long = 0,
    @Volatile var antallBatcher: Int = 0,
    @Volatile var sisteBatchSize: Int = 0,
    @Volatile var personCacheSize: Int = 0,
    val gjelderPeriodeToCount: ConcurrentHashMap<String, Int> = ConcurrentHashMap(),
    val registreringstidspunktToCount: ConcurrentHashMap<String, Int> = ConcurrentHashMap(),
    val hendelsetypeToCount: ConcurrentHashMap<String, Int> = ConcurrentHashMap(),
    val identifikatorDuplikatToHendelse: ConcurrentHashMap<String, MutableList<Hendelse>> = ConcurrentHashMap(),
    var periodeFilter: String? = "2024"
) : JobMonitor.Stats {
    fun registerHendelseStats(hendelse: Hendelse) {
        totaltAntallHendelser++
        gjelderPeriodeToCount.incrementCount(hendelse.gjelderPeriode)
        registreringstidspunktToCount.incrementCount(hendelse.registreringstidspunktAsYearMonth())
        val hendelseList = identifikatorDuplikatToHendelse.getOrDefault(hendelse.identifikator, mutableListOf())
        hendelseList.add(hendelse)
        identifikatorDuplikatToHendelse[hendelse.identifikator] = hendelseList
    }

    override fun reset() {
        totaltAntallHendelser = 0
        personerFunnet = 0
        sisteSekvensnummer = 0
        antallBatcher = 0
        sisteBatchSize = 0
        personCacheSize = 0
        gjelderPeriodeToCount.clear()
        registreringstidspunktToCount.clear()
        hendelsetypeToCount.clear()
        identifikatorDuplikatToHendelse.clear()
    }

    override fun asMap(): Map<String, Any> {
        val identifikatorsWithMoreThanOnePeriode = identifikatorDuplikatToHendelse
            .asSequence()
            .map { (identifikator, hendelser) ->
                val count = hendelser.count { it.gjelderPeriode == periodeFilter }
                Triple(identifikator, hendelser, count)
            }
            .filter { (_, _, count) -> count > 1 }
            .sortedByDescending { (_, _, count) -> count }
            .toList()

        return mapOf(
            "personCache" to personCacheSize,
            "totaltAntallHendelser" to totaltAntallHendelser,
            "personerFunnet" to personerFunnet,
            "sisteSekvensnummer" to sisteSekvensnummer,
            "antallBatcher" to antallBatcher,
            "sisteBatchSize" to sisteBatchSize,
            "identifikatorDuplikatCount" to identifikatorDuplikatToHendelse.size,
            "hendelsetypeToCount" to hendelsetypeToCount,
            "gjelderPeriodeToCount" to gjelderPeriodeToCount,
            "registreringstidspunktToCount" to registreringstidspunktToCount,

            "identifikatorToCount" to identifikatorDuplikatToHendelse
                .filter { it.value.size > 1 }
                .entries
                .sortedByDescending { it.value.size }
                .take(100)
                .associate { it.key to it.value.size },

            "moreThanOne${periodeFilter}PeriodeCount" to identifikatorsWithMoreThanOnePeriode.size,

            "identifikatorToMoreThanOne${periodeFilter}PeriodeCount" to identifikatorsWithMoreThanOnePeriode
                .take(100)
                .associate { (identifikator, _, count) -> identifikator to count },

            "identifikatorToHendelse${periodeFilter}Periode" to identifikatorDuplikatToHendelse
                .asSequence()
                .map { (identifikator, hendelser) ->
                    identifikator to hendelser.filter { it.gjelderPeriode == periodeFilter }
                }
                .filter { (_, filteredHendelser) -> filteredHendelser.size > 1 }
                .sortedByDescending { (_, filteredHendelser) -> filteredHendelser.size }
                .take(10)
                .associate { (key, filteredHendelser) ->
                    key to filteredHendelser.map { hendelse ->
                        mapOf(
                            "sekvensnummer" to hendelse.sekvensnummer,
                            "gjelderPeriode" to hendelse.gjelderPeriode,
                            "registreringstidspunkt" to hendelse.registreringstidspunkt,
                        )
                    }
                }
        )
    }
}

