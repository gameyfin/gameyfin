plugins {
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization")
}

// The development plugin layout copies dependency classes into the classes directory.
// The converter locates its native resources relative to that same code source.
tasks.named<Copy>("copyDependencyClasses") {
    from(configurations.runtimeClasspath.get().map { zipTree(it) }) {
        include("natives/**")
    }
}

dependencies {
    implementation("io.xberg:html-to-markdown:3.21.2")
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
