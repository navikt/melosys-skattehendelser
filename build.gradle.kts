import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.4"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.spring") version "1.9.23"
    kotlin("plugin.jpa") version "1.9.23"
}

group = "no.nav.melosys"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}


allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.Embeddable")
    annotation("jakarta.persistence.MappedSuperclass")
}

object dependencyVersions {
    const val kotestVersion = "5.5.4"
    const val logstashLogbackEncoder = "7.2"
    const val kotlinLogging = "3.0.5"
    const val wiremock = "3.0.1"
    const val awaitility = "4.2.0"
    const val mockk = "1.12.5"
    const val tokenSupport = "3.2.0"
}

dependencies {
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.flywaydb:flyway-core")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("net.logstash.logback:logstash-logback-encoder:${dependencyVersions.logstashLogbackEncoder}")
    implementation("io.github.microutils:kotlin-logging-jvm:${dependencyVersions.kotlinLogging}")
    implementation("no.nav.security:token-validation-spring:${dependencyVersions.tokenSupport}")
    implementation("no.nav.security:token-client-spring:${dependencyVersions.tokenSupport}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("io.kotest:kotest-assertions-json-jvm:${dependencyVersions.kotestVersion}")
    testImplementation("io.kotest:kotest-assertions-core-jvm:${dependencyVersions.kotestVersion}")
    testImplementation("com.github.tomakehurst:wiremock-standalone:${dependencyVersions.wiremock}")
    testImplementation("org.awaitility:awaitility-kotlin:${dependencyVersions.awaitility}")
    testImplementation("io.mockk:mockk:${dependencyVersions.mockk}")
    testImplementation("no.nav.security:token-validation-spring-test:${dependencyVersions.tokenSupport}")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
