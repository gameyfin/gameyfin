import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.2"
    id("io.spring.dependency-management") version "1.1.4"
    id("com.vaadin") version "24.3.3"
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.spring") version "1.9.22"
    kotlin("plugin.jpa") version "1.9.22"
    id("io.freefair.lombok") version "8.4"
    java
}

allOpen {
    annotations("javax.persistence.Entity", "javax.persistence.MappedSuperclass", "javax.persistence.Embedabble")
}

group = "de.grimsi"
version = "2.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        name = "Vaadin Addons"
        url = uri("https://maven.vaadin.com/vaadin-addons")
    }
}

extra["vaadinVersion"] = "24.3.3"

dependencies {
    // Sprint Boot
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.3")

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.github.paulcwarren:spring-content-fs-boot-starter:3.0.7")

    // Frontend
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.vaadin:vaadin-spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.github.mvysny.karibudsl:karibu-dsl-v23:2.1.0")
    implementation("com.github.mvysny.karibu-tools:karibu-tools-23:0.19")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Vaadin Add-Ons
    implementation("com.flowingcode.addons:font-awesome-iron-iconset:5.2.2")
    implementation("in.virit:viritin:2.7.0")
    implementation("io.sunshower.aire:aire-wizard:1.0.17.Final")

    // Development
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")

    // Fix compilation error
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.8.3")
}

dependencyManagement {
    imports {
        mavenBom("com.vaadin:vaadin-bom:${property("vaadinVersion")}")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
