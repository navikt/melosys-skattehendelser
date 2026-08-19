package no.nav.melosysskattehendelser

import org.springframework.boot.fromApplication
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.with
import org.springframework.context.annotation.Bean
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestMelosysSkattehendelserApplication {

    @Bean
    @ServiceConnection
    fun kafkaContainer(): ConfluentKafkaContainer {
        return ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"))
    }

}

fun main(args: Array<String>) {
    fromApplication<MelosysSkattehendelserApplication>().with(TestMelosysSkattehendelserApplication::class).run(*args)
}
