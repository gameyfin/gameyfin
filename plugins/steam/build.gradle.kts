val ktor_version = "3.0.0"

plugins {
    id("com.google.devtools.ksp") version "2.0.20-1.0.24"
    kotlin("plugin.serialization")
}

repositories {
    maven(url = "https://jitpack.io")
}

dependencies {
    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:2.0.20-1.0.1")

    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")

    implementation("me.xdrop:fuzzywuzzy:1.4.0")
}