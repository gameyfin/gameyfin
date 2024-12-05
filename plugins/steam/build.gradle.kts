val ktor_version = "3.0.0"

plugins {
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization")
}

repositories {
    maven(url = "https://jitpack.io")
}

dependencies {
    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:${rootProject.extra["pf4jKspVersion"]}")

    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")

    implementation("me.xdrop:fuzzywuzzy:1.4.0")
}