plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:${rootProject.extra["pf4jKspVersion"]}")

    // IGDB API client
    implementation("io.github.husnjak:igdb-api-jvm:1.3.1")

    // Fuzzy string matching
    implementation("me.xdrop:fuzzywuzzy:1.4.0")
}