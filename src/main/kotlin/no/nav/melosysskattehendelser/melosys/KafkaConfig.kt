package no.nav.melosysskattehendelser.melosys

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.melosysskattehendelser.melosys.consumer.MelosysHendelse
import no.nav.melosysskattehendelser.melosys.consumer.VedtakHendelseMelding
import no.nav.melosysskattehendelser.sigrun.Hendelse
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.KafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer

typealias KafkaConsumerContainerFactory<T> = KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, T>>

@Configuration
class KafkaConfig(
    @Autowired private val env: Environment,
    @Value("\${melosys.kafka.brokers}") private val brokersUrl: String,
    @Value("\${melosys.kafka.security.keystorePath}") private val keystorePath: String,
    @Value("\${melosys.kafka.security.truststorePath}") private val truststorePath: String,
    @Value("\${melosys.kafka.security.credstorePassword}") private val credstorePassword: String,
) {

    @Bean
    fun kafkaTemplate(objectMapper: ObjectMapper?): KafkaTemplate<String, Hendelse> {
        val config: Map<String, Any> = producerConfig()
        val producerFactory: ProducerFactory<String, Hendelse> =
            DefaultKafkaProducerFactory(config, StringSerializer(), JsonSerializer(objectMapper))
        return KafkaTemplate(producerFactory)
    }

    @Bean
    fun melosysVedtakListenerContainerFactory(
        kafkaProperties: KafkaProperties,
        @Value("\${melosys.kafka.consumer.groupId}") groupId: String
    ): KafkaConsumerContainerFactory<MelosysHendelse> =
        kafkaListenerContainerFactory<MelosysHendelse>(kafkaProperties, groupId)

    private inline fun <reified T> kafkaListenerContainerFactory(
        kafkaProperties: KafkaProperties,
        groupId: String
    ) = ConcurrentKafkaListenerContainerFactory<String, T>().apply {
        consumerFactory = DefaultKafkaConsumerFactory(
            kafkaProperties.buildConsumerProperties(null) + consumerConfig(groupId),
            StringDeserializer(),
            valueDeserializer<T>()
        )
    }

    private inline fun <reified T> valueDeserializer(): ErrorHandlingDeserializer<T> =
        ErrorHandlingDeserializer(
            JsonDeserializer(T::class.java, false)
        )

    private fun producerConfig() = mapOf<String, Any>(
        CommonClientConfigs.CLIENT_ID_CONFIG to "skattehendelser-producer",
        ProducerConfig.ACKS_CONFIG to "all",
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to brokersUrl
    ) + securityConfig()

    private fun consumerConfig(groupId: String) = mapOf<String, Any>(
        ConsumerConfig.GROUP_ID_CONFIG to groupId,
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to brokersUrl,
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG to 15000,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JsonDeserializer::class.java,
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 1
    ) + securityConfig()

    private fun securityConfig(): Map<String, Any> =
        if (isLocal) mapOf() else mapOf<String, Any>(
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SSL",
            SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to truststorePath,
            SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to credstorePassword,
            SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to "JKS",
            SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to keystorePath,
            SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to credstorePassword,
            SslConfigs.SSL_KEY_PASSWORD_CONFIG to credstorePassword,
            SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to "PKCS12"
        )

    private val isLocal: Boolean
        get() = env.activeProfiles.any {
            it.equals("local", ignoreCase = true) ||
                    it.equals("local-mock", ignoreCase = true) ||
                    it.equals("test", ignoreCase = true)
        }


}