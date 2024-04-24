package no.nav.melosysskattehendelser.melosys

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.melosysskattehendelser.sigrun.Hendelse
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JsonSerializer
import java.util.*

@Configuration
class KafkaConfig(
    @Autowired private val env: Environment,
    @Value("\${melosys.kafka.brokers}") private val brokersUrl: String,
    @Value("\${melosys.kafka.security.keystorePath}") private val keystorePath: String,
    @Value("\${melosys.kafka.security.truststorePath}") private val truststorePath: String,
    @Value("\${melosys.kafka.security.credstorePassword}") private val credstorePassword: String
) {

    @Bean
    fun kafkaTemplate(objectMapper: ObjectMapper?): KafkaTemplate<String, Hendelse> {
        val props: Map<String, Any> = producerProps()
        val producerFactory: ProducerFactory<String, Hendelse> =
            DefaultKafkaProducerFactory(props, StringSerializer(), JsonSerializer(objectMapper))
        return KafkaTemplate(producerFactory)
    }

    private fun producerProps(): Map<String, Any> {
        val props: MutableMap<String, Any> = HashMap()
        props[CommonClientConfigs.CLIENT_ID_CONFIG] = "skattehendelser-producer"
        props[ProducerConfig.ACKS_CONFIG] = "all"
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = brokersUrl
        if (isNotLocal()) {
            props.putAll(securityConfig())
        }
        return props
    }

    private fun securityConfig(): Map<String, Any> {
        val props: MutableMap<String, Any> = HashMap()
        props[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SSL"
        props[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = truststorePath
        props[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = credstorePassword
        props[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = "JKS"
        props[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = keystorePath
        props[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = credstorePassword
        props[SslConfigs.SSL_KEY_PASSWORD_CONFIG] = credstorePassword
        props[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "PKCS12"
        return props
    }

    private fun isNotLocal(): Boolean {
        return Arrays.stream(env.activeProfiles).noneMatch { profile: String ->
            (profile.equals("local", ignoreCase = true)
                    || profile.equals("test", ignoreCase = true)
                    || profile.equals("local-mock", ignoreCase = true))
        }
    }


}