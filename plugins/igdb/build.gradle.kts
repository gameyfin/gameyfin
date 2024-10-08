plugins {
    kotlin("jvm")
}

group = "de.grimsi.gameyfin.plugins"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":plugin-api"))
}

tasks.test {
    useJUnitPlatform()
}