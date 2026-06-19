import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.spring") version "2.4.0"
    kotlin("plugin.jpa") version "2.4.0"
}

group = "no.nav.melosys"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/navikt/token-support")
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: (findProperty("gpr.user") as String?) ?: "x-access-token"
            password = System.getenv("READER_TOKEN") ?: System.getenv("GITHUB_TOKEN") ?: (findProperty("gpr.key") as String?)
        }
        content { includeGroup("no.nav.security") }
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.Embeddable")
    annotation("jakarta.persistence.MappedSuperclass")
}

object dependencyVersions {
    const val kotestVersion = "6.2.0"
    const val logstashLogbackEncoder = "9.0"
    const val kotlinLogging = "8.0.4"
    const val wiremock = "3.13.2"
    const val awaitility = "4.3.0"
    const val mockk = "1.14.11"
    const val tokenSupport = "6.0.10"
    const val shedlockVersion = "7.7.0"
    const val shedlockProvicerJdbcVersion = "7.7.0"
    const val janino = "3.1.12"
    const val micrometerVersion = "1.17.0"
    const val micrometerJvmExtrasVersion = "0.3.0"
    const val springdocVersion = "3.0.3"
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
    implementation("org.springframework.boot:spring-boot-starter-webclient")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("io.micrometer:micrometer-registry-prometheus:${dependencyVersions.micrometerVersion}")
    implementation("io.github.mweirauch:micrometer-jvm-extras:${dependencyVersions.micrometerJvmExtrasVersion}")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
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
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.postgresql:postgresql")
    testImplementation("org.testcontainers:testcontainers-postgresql")
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
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
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