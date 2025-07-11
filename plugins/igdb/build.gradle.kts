val resilience4jVersion = "2.2.0"

plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:${rootProject.extra["pf4jKspVersion"]}")

    // IGDB API client
    implementation("io.github.husnjak:igdb-api-jvm:1.3.1")

    // Resilience4j for rate limiting
    implementation("io.github.resilience4j:resilience4j-ratelimiter:${resilience4jVersion}") {
        exclude(group = "org.slf4j")
    }
    implementation("io.github.resilience4j:resilience4j-bulkhead:${resilience4jVersion}") {
        exclude(group = "org.slf4j")
    }
    implementation("io.github.resilience4j:resilience4j-all:${resilience4jVersion}") {
        exclude(group = "org.slf4j")
    }

    // Fuzzy string matching
    implementation("me.xdrop:fuzzywuzzy:1.4.0")
}