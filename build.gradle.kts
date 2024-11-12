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
    const val logstashLogbackEncoder = "8.0"
    const val kotlinLogging = "3.0.5"
    const val wiremock = "3.0.1"
    const val awaitility = "4.2.0"
    const val mockk = "1.12.5"
    const val tokenSupport = "3.2.0"
    const val shedlockVersion = "4.4.0"
    const val shedlockProvicerJdbcVersion = "4.43.0"
    const val janino = "3.1.12"
}

val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch").lowercase()

dependencies {
    runtimeOnly("org.postgresql:postgresql")
    if (osName.contains("mac") && osArch.contains("aarch64")) {
        implementation("io.netty:netty-resolver-dns-native-macos:4.1.109.Final:osx-aarch_64")
    }
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-jetty")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.flywaydb:flyway-core")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("net.logstash.logback:logstash-logback-encoder:${dependencyVersions.logstashLogbackEncoder}")
    implementation("io.github.microutils:kotlin-logging-jvm:${dependencyVersions.kotlinLogging}")
    implementation("no.nav.security:token-validation-spring:${dependencyVersions.tokenSupport}")
    implementation("no.nav.security:token-client-spring:${dependencyVersions.tokenSupport}")
    implementation("net.javacrumbs.shedlock:shedlock-spring:${dependencyVersions.shedlockVersion}")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:${dependencyVersions.shedlockProvicerJdbcVersion}")
    implementation("org.codehaus.janino:janino:${dependencyVersions.janino}")

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
