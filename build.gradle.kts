import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val springCloudVersion by extra("2023.0.3")
val vaadinVersion by extra("24.4.5")

plugins {
    val kotlinVersion = "2.0.10"
    val vaadinVersion = "24.4.5"

    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
    id("com.vaadin") version vaadinVersion
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    kotlin("plugin.jpa") version kotlinVersion
    java
}

allOpen {
    annotations("javax.persistence.Entity", "javax.persistence.MappedSuperclass", "javax.persistence.Embedabble")
}

group = "de.grimsi"
version = "2.0.0-SNAPSHOT"
description = "gameyfin"

java.sourceCompatibility = JavaVersion.VERSION_22

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
    maven {
        setUrl("https://maven.vaadin.com/vaadin-addons")
    }
}

dependencies {
    // Spring Boot & Kotlin
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Vaadin Hilla
    implementation("com.vaadin:vaadin")
    api("com.vaadin:vaadin-spring-boot-starter")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.3")

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.github.paulcwarren:spring-content-fs-boot-starter:3.0.7")
    implementation("org.springframework.cloud:spring-cloud-starter")

    // Development
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

dependencyManagement {
    imports {
        mavenBom("com.vaadin:vaadin-bom:$vaadinVersion")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile> {
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        jvmTarget.set(JvmTarget.JVM_22)
        progressiveMode.set(true)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
