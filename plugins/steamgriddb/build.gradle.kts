plugins {
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization")
}

dependencies {
    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:${rootProject.extra["pf4jKspVersion"]}")

    implementation("io.ktor:ktor-client-core:${rootProject.extra["ktorVersion"]}") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("io.ktor:ktor-client-cio:${rootProject.extra["ktorVersion"]}") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("io.ktor:ktor-client-content-negotiation:${rootProject.extra["ktorVersion"]}") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("io.ktor:ktor-serialization-kotlinx-json:${rootProject.extra["ktorVersion"]}") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
}