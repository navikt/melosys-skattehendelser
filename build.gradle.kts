import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
    kotlin("plugin.jpa") version "2.2.0"
}

group = "no.nav.melosys"

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
    const val kotestVersion = "5.9.1"
    const val logstashLogbackEncoder = "8.1"
    const val kotlinLogging = "7.0.13"
    const val wiremock = "3.13.1"
    const val awaitility = "4.3.0"
    const val mockk = "1.13.16"
    const val tokenSupport = "3.2.0"
    const val shedlockVersion = "6.10.0"
    const val shedlockProvicerJdbcVersion = "6.10.0"
    const val janino = "3.1.12"
    const val micrometerVersion = "1.15.1"
    const val micrometerJvmExtrasVersion = "0.2.2"
    const val springdocVersion = "2.2.0"
}

val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch").lowercase()

dependencies {
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
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
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.micrometer:micrometer-registry-prometheus:${dependencyVersions.micrometerVersion}")
    implementation("io.github.mweirauch:micrometer-jvm-extras:${dependencyVersions.micrometerJvmExtrasVersion}")
    implementation("org.flywaydb:flyway-core")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("net.logstash.logback:logstash-logback-encoder:${dependencyVersions.logstashLogbackEncoder}")
    implementation("io.github.oshai:kotlin-logging-jvm:${dependencyVersions.kotlinLogging}")
    implementation("no.nav.security:token-validation-spring:${dependencyVersions.tokenSupport}")
    implementation("no.nav.security:token-client-spring:${dependencyVersions.tokenSupport}")
    implementation("net.javacrumbs.shedlock:shedlock-spring:${dependencyVersions.shedlockVersion}")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:${dependencyVersions.shedlockProvicerJdbcVersion}")
    implementation("org.codehaus.janino:janino:${dependencyVersions.janino}")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${dependencyVersions.springdocVersion}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.postgresql:postgresql")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("io.kotest:kotest-assertions-json-jvm:${dependencyVersions.kotestVersion}")
    testImplementation("io.kotest:kotest-assertions-core-jvm:${dependencyVersions.kotestVersion}")
    testImplementation("org.wiremock:wiremock-standalone:${dependencyVersions.wiremock}")
    testImplementation("org.awaitility:awaitility-kotlin:${dependencyVersions.awaitility}")
    testImplementation("io.mockk:mockk:${dependencyVersions.mockk}")
    testImplementation("no.nav.security:token-validation-spring-test:${dependencyVersions.tokenSupport}")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("melosys-skattehendelser")
    archiveVersion.set("")
}

// Do not need plain.jar
tasks.named<Jar>("jar") {
    enabled = false
}