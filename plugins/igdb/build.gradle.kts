plugins {
    kotlin("jvm")
    id("kotlin-kapt")
}

group = "de.grimsi.gameyfin.plugins"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":plugin-api"))
}

tasks.jar {
    manifest {
        from("./src/main/resources/MANIFEST.MF")
    }
}

tasks.test {
    useJUnitPlatform()
}