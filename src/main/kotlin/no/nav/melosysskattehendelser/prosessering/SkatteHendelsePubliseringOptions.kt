package no.nav.melosysskattehendelser.prosessering

data class SkatteHendelsePubliseringOptions(
    val useCache: Boolean = false,
    val confirmationCode: String? = null
)
