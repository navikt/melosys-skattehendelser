package no.nav.melosysskattehendelser.prosessering

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Service
class JobConfirmationService(
    environment: Environment,
    @Value("\${app.dry-run-publisering}") val dryRunPublisering: Boolean
) {
    private val tokens = ConcurrentHashMap<String, Instant>()
    val naisClusterName = environment.getProperty("NAIS_CLUSTER_NAME") ?: "local"

    val isProd = naisClusterName.contains("prod") == true

    fun generateToken(): String {
        val token = UUID.randomUUID().toString().split("-")[0]
        tokens[token] = Instant.now()
        return token
    }

    fun isValid(token: String?): Boolean {
        if (!isProd) return true
        val nonNullToken = token ?: return false
        val timestamp = tokens[nonNullToken] ?: return false
        val isValid = Instant.now().isBefore(timestamp.plusSeconds(300))
        if (isValid) tokens.remove(nonNullToken)
        return isValid
    }

    fun confirmationMessage(token: String): String =
        "⚠️ Cluster: $naisClusterName, isProd: $isProd. dryRunPublisering:$dryRunPublisering\n" +
                "To confirm, resend with confirmationCode=\"$token\" (valid for 5 minutes)"
}
