//package no.nav.melosysskattehendelser.prosessering
//
//import no.nav.melosysskattehendelser.skatt.Hendelse
//import java.util.Collections
//import java.util.concurrent.ConcurrentHashMap
//import kotlin.collections.component1
//import kotlin.collections.component2
//import kotlin.collections.set
//
//data class SkatteHendelsePubliseringStats(
//    @Volatile var totaltAntallHendelser: Int = 0,
//    @Volatile var personerFunnet: Int = 0,
//    @Volatile var sisteSekvensnummer: Long = 0,
//    @Volatile var antallBatcher: Int = 0,
//    @Volatile var sisteBatchSize: Int = 0,
//    @Volatile var personCacheSize: Int = 0,
//    val gjelderPeriodeToCount: ConcurrentHashMap<String, Int> = ConcurrentHashMap(),
//    val registreringstidspunktToCount: ConcurrentHashMap<String, Int> = ConcurrentHashMap(),
//    val hendelsetypeToCount: ConcurrentHashMap<String, Int> = ConcurrentHashMap(),
//    val identifikatorDuplikatToHendelse: ConcurrentHashMap<String, MutableList<Hendelse>> = ConcurrentHashMap(),
//    val skatteHendelseIdentMatch: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap()),
//    var periodeFilter: String? = "2024",
//    var kunIdentMatch: Boolean = false,
//) : JobMonitor.Stats {
//    fun registerHendelseStats(hendelse: Hendelse, ident: String? = null) {
//        totaltAntallHendelser++
//        if (ident != null) skatteHendelseIdentMatch.add(ident)
//        gjelderPeriodeToCount.incrementCount(hendelse.gjelderPeriode)
//        registreringstidspunktToCount.incrementCount(hendelse.registreringstidspunktAsYearMonth())
//        val hendelseList = identifikatorDuplikatToHendelse.getOrDefault(hendelse.identifikator, mutableListOf())
//        hendelseList.add(hendelse)
//        identifikatorDuplikatToHendelse[hendelse.identifikator] = hendelseList
//    }
//
//    data class MethodTimingStatus(
//        val method: String,
//        val count: Long,
//        val totalTimeMs: Double,
//        val maxTimeMs: Double
//    )
//    fun timings(): List<MethodTimingStatus> {
//        return meterRegistry.get("method.execution.time").meters.mapNotNull { meter ->
//            val timer = meter as? Timer ?: return@mapNotNull null
//            val method = meter.id.getTag("method") ?: return@mapNotNull null
//            MethodTimingStatus(
//                method = method,
//                count = timer.count(),
//                totalTimeMs = timer.totalTime(TimeUnit.MILLISECONDS),
//                maxTimeMs = timer.max(TimeUnit.MILLISECONDS)
//            )
//        }.sortedByDescending { it.maxTimeMs } // Optional: sort by slowness
//    }
//
//    override fun reset() {
//        totaltAntallHendelser = 0
//        personerFunnet = 0
//        sisteSekvensnummer = 0
//        antallBatcher = 0
//        sisteBatchSize = 0
//        personCacheSize = 0
//        gjelderPeriodeToCount.clear()
//        registreringstidspunktToCount.clear()
//        hendelsetypeToCount.clear()
//        identifikatorDuplikatToHendelse.clear()
//    }
//
//    override fun asMap(): Map<String, Any> {
//        val identifikatorsWithMoreThanOnePeriode = identifikatorDuplikatToHendelse
//            .asSequence()
//            .filter { !kunIdentMatch || it.key in skatteHendelseIdentMatch  }
//            .map { (identifikator, hendelser) ->
//                val count = hendelser.count { it.gjelderPeriode == periodeFilter }
//                Triple(identifikator, hendelser, count)
//            }
//            .filter { (_, _, count) -> count > 1 }
//            .sortedByDescending { (_, _, count) -> count }
//            .toList()
//
//        return mapOf(
//            "personCache" to personCacheSize,
//            "totaltAntallHendelser" to totaltAntallHendelser,
//            "personerFunnet" to personerFunnet,
//            "sisteSekvensnummer" to sisteSekvensnummer,
//            "antallBatcher" to antallBatcher,
//            "sisteBatchSize" to sisteBatchSize,
//            "identifikatorDuplikatCount" to identifikatorDuplikatToHendelse
//                .filter { it.value.size > 1 }
//                .filter { !kunIdentMatch || it.key in skatteHendelseIdentMatch  }
//                .size,
//            "hendelsetypeToCount" to hendelsetypeToCount,
//            "gjelderPeriodeToCount" to gjelderPeriodeToCount,
//            "registreringstidspunktToCount" to registreringstidspunktToCount,
//
//            "identifikatorToCount" to identifikatorDuplikatToHendelse
//                .filter { !kunIdentMatch || it.key in skatteHendelseIdentMatch  }
//                .filter { it.value.size > 1 }
//                .entries
//                .sortedByDescending { it.value.size }
//                .take(100)
//                .associate { it.key to it.value.size },
//
//            "moreThanOne${periodeFilter}PeriodeCount" to identifikatorsWithMoreThanOnePeriode.size,
//
//            "identifikatorToMoreThanOne${periodeFilter}PeriodeCount" to identifikatorsWithMoreThanOnePeriode
//                .take(100)
//                .associate { (identifikator, _, count) -> identifikator to count },
//
//            "identifikatorToHendelse${periodeFilter}Periode" to identifikatorDuplikatToHendelse
//                .asSequence()
//                .filter { !kunIdentMatch || it.key in skatteHendelseIdentMatch  }
//                .map { (identifikator, hendelser) ->
//                    identifikator to hendelser.filter { it.gjelderPeriode == periodeFilter }
//                }
//                .filter { (_, filteredHendelser) -> filteredHendelser.size > 1 }
//                .sortedByDescending { (_, filteredHendelser) -> filteredHendelser.size }
//                .take(10)
//                .associate { (key, filteredHendelser) ->
//                    key to filteredHendelser.map { hendelse ->
//                        mapOf(
//                            "sekvensnummer" to hendelse.sekvensnummer,
//                            "gjelderPeriode" to hendelse.gjelderPeriode,
//                            "registreringstidspunkt" to hendelse.registreringstidspunkt,
//                        )
//                    }
//                }
//        )
//    }
//}

