package no.nav.melosysskattehendelser.prosessering

fun <K> MutableMap<K, Int>.incrementCount(key: K, incrementBy: Int = 1) {
    this[key] = getOrDefault(key, 0) + incrementBy
}
