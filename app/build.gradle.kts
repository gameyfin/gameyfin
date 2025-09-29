group = "de.grimsi"
val appMainClass = "org.gameyfin.app.GameyfinApplicationKt"

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.vaadin")
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("com.google.devtools.ksp")
    application
}

application {
    mainClass.set(appMainClass)
}

allOpen {
    annotations("javax.persistence.Entity", "javax.persistence.MappedSuperclass", "javax.persistence.Embedabble")
}

repositories {
    maven {
        setUrl("https://maven.vaadin.com/vaadin-addons")
    }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.cloud:spring-cloud-starter")
    implementation("jakarta.validation:jakarta.validation-api:3.1.0")

    // Kotlin extensions
    implementation(kotlin("reflect"))

    // Reactive
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Vaadin Hilla
    implementation("com.vaadin:vaadin-core") {
        exclude("com.vaadin:flow-react")
    }
    implementation("com.vaadin:vaadin-spring-boot-starter")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")

    // Persistence & I/O
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.github.paulcwarren:spring-content-fs-boot-starter:3.0.17")
    implementation("org.flywaydb:flyway-core")
    implementation("commons-io:commons-io:2.18.0")

    // SSO
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")

    // Notifications
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("ch.digitalfondue.mjml4j:mjml4j:1.0.3")

    // Plugins
    implementation(project(":plugin-api"))

    // Utils
    implementation("org.apache.tika:tika-core:3.1.0")
    implementation("me.xdrop:fuzzywuzzy:1.4.0")

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
        mavenBom("com.vaadin:vaadin-bom:${rootProject.extra["vaadinVersion"]}")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${rootProject.extra["springCloudVersion"]}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}