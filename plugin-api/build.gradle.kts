plugins {
    kotlin("jvm")
}

group = "de.grimsi.gameyfin"

repositories {
    mavenCentral()
}

dependencies {
    // PF4J (shared)
    api("org.pf4j:pf4j:${rootProject.extra["pf4jVersion"]}") {
        exclude(group = "org.slf4j")
    }
    
    api("org.slf4j:slf4j-api:2.0.16")

    implementation(kotlin("stdlib"))

    // Test dependencies
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}