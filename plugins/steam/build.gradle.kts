val ktor_version = "3.1.3"

plugins {
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization")
}

dependencies {
    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:${rootProject.extra["pf4jKspVersion"]}")

    implementation("io.ktor:ktor-client-core:$ktor_version") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("io.ktor:ktor-client-cio:$ktor_version") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    implementation("me.xdrop:fuzzywuzzy:1.4.0")
}