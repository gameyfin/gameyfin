import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.vaadin")
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    java
}

allOpen {
    annotations("javax.persistence.Entity", "javax.persistence.MappedSuperclass", "javax.persistence.Embedabble")
}

group = "de.grimsi"
version = "2.0.0-SNAPSHOT"
description = "gameyfin"

java.sourceCompatibility = JavaVersion.VERSION_21

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
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Reactive
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Vaadin Hilla
    implementation("com.vaadin:vaadin-core") {
        exclude("com.vaadin:flow-react")
    }
    api("com.vaadin:vaadin-spring-boot-starter")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.3")

    // Persistence & I/O
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.github.paulcwarren:spring-content-fs-boot-starter:3.0.14")
    implementation("org.springframework.cloud:spring-cloud-starter")
    implementation("commons-io:commons-io:2.16.1")

    // SSO
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")

    // Notifications
    implementation("org.springframework.boot:spring-boot-starter-mail")

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
        mavenBom("com.vaadin:vaadin-bom:${extra["vaadinVersion"]}")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${extra["springCloudVersion"]}")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile> {
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        jvmTarget.set(JvmTarget.JVM_21)
        progressiveMode.set(true)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
