package no.nav.melosysskattehendelser.prosessering

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import mu.KotlinLogging
import org.springframework.core.env.Environment

private val log = KotlinLogging.logger { }

@JsonIgnoreProperties(ignoreUnknown = true)
data class SkatteHendelsePubliseringOptions(
    val dryRun: Boolean = false,
    val filterSaksnummer: String? = null
) {
    companion object {
        fun av(environment: Environment): SkatteHendelsePubliseringOptions {
            val naisClusterName = environment.getProperty(NAIS_CLUSTER_NAME)
            val isProd: Boolean = naisClusterName?.contains("prod") == true
            log.info { "NAIS_CLUSTER_NAME=$naisClusterName, isProd=$isProd, dryRun:${isProd}" }

            return SkatteHendelsePubliseringOptions(
                dryRun = isProd
            )
        }

        const val NAIS_CLUSTER_NAME = "NAIS_CLUSTER_NAME"
    }
}
