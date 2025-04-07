package no.nav.melosysskattehendelser.prosessering

import mu.KotlinLogging
import org.springframework.core.env.Environment

private val log = KotlinLogging.logger { }

data class SkatteHendelsePubliseringOptions(
    val useCache: Boolean = false
) {
    companion object {
        fun av(environment: Environment): SkatteHendelsePubliseringOptions {
            val naisClusterName = environment.getProperty(NAIS_CLUSTER_NAME)
            val isProd: Boolean = naisClusterName?.contains("prod") == true
            log.info { "NAIS_CLUSTER_NAME=$naisClusterName, isProd=$isProd" }

            return SkatteHendelsePubliseringOptions()
        }

        const val NAIS_CLUSTER_NAME = "NAIS_CLUSTER_NAME"
    }
}
