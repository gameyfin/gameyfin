val ktor_version = "3.1.3"
val resilience4jVersion = "2.2.0"

plugins {
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization")
}

dependencies {
    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:${rootProject.extra["pf4jKspVersion"]}")

    implementation("io.ktor:ktor-client-core:$ktor_version") {
        exclude(group = "org.slf4j")
    }
    implementation("io.ktor:ktor-client-cio:$ktor_version") {
        exclude(group = "org.slf4j")
    }
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version") {
        exclude(group = "org.slf4j")
    }
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version") {
        exclude(group = "org.slf4j")
    }

    // Resilience4j for rate limiting and bulkheading
    implementation("io.github.resilience4j:resilience4j-ratelimiter:${resilience4jVersion}") {
        exclude(group = "org.slf4j")
    }
    implementation("io.github.resilience4j:resilience4j-bulkhead:${resilience4jVersion}") {
        exclude(group = "org.slf4j")
    }
    implementation("io.github.resilience4j:resilience4j-all:${resilience4jVersion}") {
        exclude(group = "org.slf4j")
    }

    implementation("me.xdrop:fuzzywuzzy:1.4.0")
    implementation("org.jsoup:jsoup:1.20.1")
}