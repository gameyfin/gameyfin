import org.apache.tools.ant.filters.ReplaceTokens

group = "org.gameyfin"
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
    jacoco
    id("org.sonarqube")
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
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("org.springframework.cloud:spring-cloud-starter")
    implementation("jakarta.validation:jakarta.validation-api:${rootProject.extra["jakartaValidationVersion"]}")

    // Kotlin extensions
    implementation(kotlin("reflect"))

    // Reactive
    implementation("org.springframework.boot:spring-boot-starter-webflux") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-reactor-netty")
    }
    implementation("org.springframework.boot:spring-boot-starter-jetty")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Vaadin Hilla
    implementation("com.vaadin:vaadin-core") {
        exclude("com.vaadin:flow-react")
    }
    implementation("com.vaadin:vaadin-spring-boot-starter") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("com.vaadin:hilla-spring-boot-starter")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:${rootProject.extra["kotlinLoggingVersion"]}")

    // Persistence & I/O
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("commons-io:commons-io:${rootProject.extra["commonsIoVersion"]}")
    implementation("com.google.guava:guava:${rootProject.extra["guavaVersion"]}")

    // SSO
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")

    // Notifications
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("ch.digitalfondue.mjml4j:mjml4j:${rootProject.extra["mjml4jVersion"]}")

    // Plugins
    implementation(project(":plugin-api"))

    // Utils
    implementation("org.apache.tika:tika-core:${rootProject.extra["tikaVersion"]}")
    implementation("me.xdrop:fuzzywuzzy:${rootProject.extra["fuzzywuzzyVersion"]}")
    implementation("com.vanniktech:blurhash:${rootProject.extra["blurhashVersion"]}")

    // Development
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("com.vaadin:vaadin-dev")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito", module = "mockito-core")
    }
    testImplementation("io.mockk:mockk:${rootProject.extra["mockkVersion"]}")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.projectreactor:reactor-test")
}

dependencyManagement {
    imports {
        mavenBom("com.vaadin:vaadin-bom:${rootProject.extra["vaadinVersion"]}")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${rootProject.extra["springCloudVersion"]}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        xml.outputLocation = layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml")
    }
}

tasks.named("sonar") {
    dependsOn(tasks.jacocoTestReport)
}

sonar {
    properties {
        property("sonar.organization", "gameyfin")
        property("sonar.projectKey", "gameyfin_gameyfin")
        property("sonar.projectName", "gameyfin")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
        property("sonar.coverage.exclusions", "**/*Config.kt,**/org/gameyfin/db/h2/**")
    }
}

tasks.named<ProcessResources>("processResources") {
    val projectVersion = rootProject.version.toString()
    filesMatching("application.yml") {
        filter<ReplaceTokens>("tokens" to mapOf("project.version" to projectVersion))
    }
}

