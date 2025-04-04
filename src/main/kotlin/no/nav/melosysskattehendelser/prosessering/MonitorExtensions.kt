package no.nav.melosysskattehendelser.prosessering

import java.util.concurrent.atomic.AtomicLong

inline fun <T> measure(
    statsMap: MutableMap<String, AtomicLong>,
    methodName: String,
    block: () -> T
): T {
    val start = System.nanoTime()
    try {
        return block()
    } finally {
        val duration = System.nanoTime() - start
        statsMap.computeIfAbsent(methodName) { AtomicLong(0) }.addAndGet(duration)
    }
}

fun <K> MutableMap<K, Int>.incrementCount(key: K, incrementBy: Int = 1) {
    this[key] = getOrDefault(key, 0) + incrementBy
}
