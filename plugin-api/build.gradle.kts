plugins {
    kotlin("jvm")
}

group = "de.grimsi.gameyfin"

repositories {
    mavenCentral()
}

dependencies {
    // PF4J (shared)
    api("org.pf4j:pf4j:${rootProject.extra["pf4jVersion"]}")

    implementation(kotlin("stdlib"))

    // Test dependencies
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}