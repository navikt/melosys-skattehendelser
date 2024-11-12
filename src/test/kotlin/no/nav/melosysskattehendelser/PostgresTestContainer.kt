package no.nav.melosysskattehendelser

import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.PostgreSQLContainer

object PostgresTestContainer {
    private val container: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:15.2")
    private const val USE_CONTAINER = true // easy way to switch to run against local docker

    init {
        if (useTestContainer()) {
            container.start() // Start the container when this object is accessed
        }
    }

    private fun useTestContainer(): Boolean = 
        System.getenv("USE_LOCAL_DB")?.lowercase() != "true" && USE_CONTAINER

    fun registerPostgresProperties(registry: DynamicPropertyRegistry) {
        if (useTestContainer()) {
            registry.add("spring.datasource.url") { container.jdbcUrl }
            registry.add("spring.datasource.username") { container.username }
            registry.add("spring.datasource.password") { container.password }
        }
    }
}