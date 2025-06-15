import com.vanniktech.maven.publish.SonatypeHost

plugins {
    kotlin("jvm")
    `java-library`
    id("com.vanniktech.maven.publish") version "0.32.0"
}

group = "org.gameyfin"

dependencies {
    // PF4J (shared)
    api("org.pf4j:pf4j:${rootProject.extra["pf4jVersion"]}")
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates(project.group.toString(), project.name, project.version.toString())

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