plugins {
    kotlin("jvm")
    `java-library`
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "org.gameyfin"

dependencies {
    // PF4J (shared)
    api("org.pf4j:pf4j:${rootProject.extra["pf4jVersion"]}")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:${rootProject.extra["kotlinLoggingVersion"]}")

    // JSON serialization
    implementation("tools.jackson.module:jackson-module-kotlin:${rootProject.extra["jacksonVersion"]}")
    compileOnly("tools.jackson.core:jackson-databind:${rootProject.extra["jacksonVersion"]}")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(project.group.toString(), project.name, rootProject.version.toString())

    pom {
        name = "Gameyfin Plugin API"
        description =
            "The Gameyfin Plugin API provides the necessary interfaces and classes to create plugins for Gameyfin."
        url = "https://gameyfin.org/"

        licenses {
            license {
                name = "GNU  Affero General Public License v3.0"
                url = "https://www.gnu.org/licenses/agpl-3.0.en.html"
            }
        }

        developers {
            developer {
                id = "grimsi"
                name = "Simon Grimme"
                url = "https://github.com/grimsi"
            }
        }

        scm {
            url = "https://github.com/gameyfin/gameyfin"
            connection = "scm:git:git://github.com/gameyfin/gameyfin.git"
            developerConnection = "scm:git:ssh://git@github.com/gameyfin/gameyfin.git"
        }
    }
}