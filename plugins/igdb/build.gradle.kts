plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:${rootProject.extra["pf4jKspVersion"]}")

    // IGDB API client
    implementation("io.github.husnjak:igdb-api-jvm:${rootProject.extra["igdbApiJvmVersion"]}")

    // Resilience4j for rate limiting
    implementation("io.github.resilience4j:resilience4j-ratelimiter:${rootProject.extra["resilience4jVersion"]}")
    implementation("io.github.resilience4j:resilience4j-bulkhead:${rootProject.extra["resilience4jVersion"]}")
    implementation("io.github.resilience4j:resilience4j-all:${rootProject.extra["resilience4jVersion"]}")

    // Fuzzy string matching
    implementation("me.xdrop:fuzzywuzzy:${rootProject.extra["fuzzywuzzyVersion"]}")
}
