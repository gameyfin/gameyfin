plugins {
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization")
}

dependencies {
    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:${rootProject.extra["pf4jKspVersion"]}")

    implementation("io.ktor:ktor-client-core:${rootProject.extra["ktorVersion"]}") {
        exclude(group = "org.slf4j")
    }
    implementation("io.ktor:ktor-client-cio:${rootProject.extra["ktorVersion"]}") {
        exclude(group = "org.slf4j")
    }
    implementation("io.ktor:ktor-client-content-negotiation:${rootProject.extra["ktorVersion"]}") {
        exclude(group = "org.slf4j")
    }
    implementation("io.ktor:ktor-serialization-kotlinx-json:${rootProject.extra["ktorVersion"]}") {
        exclude(group = "org.slf4j")
    }

    // Resilience4j for rate limiting and bulkheading
    implementation("io.github.resilience4j:resilience4j-ratelimiter:${rootProject.extra["resilience4jVersion"]}") {
        exclude(group = "org.slf4j")
    }
    implementation("io.github.resilience4j:resilience4j-bulkhead:${rootProject.extra["resilience4jVersion"]}") {
        exclude(group = "org.slf4j")
    }
    implementation("io.github.resilience4j:resilience4j-all:${rootProject.extra["resilience4jVersion"]}") {
        exclude(group = "org.slf4j")
    }

    implementation("me.xdrop:fuzzywuzzy:${rootProject.extra["fuzzywuzzyVersion"]}")
    implementation("org.jsoup:jsoup:${rootProject.extra["jsoupVersion"]}")
}