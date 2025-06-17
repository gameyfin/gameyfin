import java.util.jar.Manifest

plugins {
    kotlin("jvm")
}

tasks.named<Jar>("jar") {
    enabled = false
}

val keystorePasswordEnvironmentVariable = "GAMEYFIN_KEYSTORE_PASSWORD"
val keystorePasswordProperty = "gameyfin.keystorePassword"

val keystorePath: String = rootProject.file("certs/gameyfin.jks").absolutePath
val keystoreAlias = "gameyfin-plugins"
val keystorePassword: String = (findProperty(keystorePasswordProperty) as String?)
    ?: System.getenv(keystorePasswordEnvironmentVariable)
    ?: ""

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        compileOnly(project(":plugin-api"))
    }

    // Read the version from the MANIFEST.MF file in resources
    val manifestFile = file("src/main/resources/MANIFEST.MF")
    val manifestVersion: String? = if (manifestFile.exists()) {
        Manifest(manifestFile.inputStream()).mainAttributes.getValue("Plugin-Version")
    } else null
    version = manifestVersion ?: "1.0-SNAPSHOT"

    tasks.jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        isZip64 = true
        archiveBaseName.set(project.name)

        manifest {
            from("./src/main/resources/MANIFEST.MF")
        }

        from(configurations.runtimeClasspath.get().map { project.zipTree(it) }) {
            exclude("META-INF/*.SF")
            exclude("META-INF/*.DSA")
            exclude("META-INF/*.RSA")
        }
        from(sourceSets["main"].output.classesDirs)
        from(sourceSets["main"].resources)

        // Include logo file under META-INF/resources
        from("src/main/resources") {
            include("logo.*")
            into("META-INF/resources")
        }
    }

    tasks.register<Copy>("copyDependencyClasses") {
        dependsOn(tasks.jar)

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        from(configurations.runtimeClasspath.get().map { project.zipTree(it) }) {
            include("**/*.class")
        }
        into(layout.buildDirectory.get().asFile.resolve("classes/kotlin/main"))
    }

    tasks.build {
        dependsOn("copyDependencyClasses")
    }


    tasks.register<Exec>("signJar") {
        dependsOn(tasks.jar)

        if ((findProperty("vaadin.productionMode") as String?) == "true" && keystorePassword.isEmpty()) {
            throw GradleException("Keystore password must be provided when vaadin.productionMode is true. Use -P$keystorePasswordProperty=your_password or set the $keystorePasswordEnvironmentVariable environment variable.")
        }

        val jarFile = tasks.jar.get().archiveFile.get().asFile

        // Only enable if password is present
        enabled = keystorePassword.isNotEmpty()

        commandLine(
            "jarsigner",
            "-keystore", keystorePath,
            "-storepass", keystorePassword,
            jarFile.absolutePath,
            keystoreAlias
        )
        doFirst {
            if (keystorePassword.isEmpty()) {
                logger.lifecycle("Keystore password not provided, skipping JAR signing.")
            }
        }
    }

    tasks.build {
        dependsOn("signJar")
    }
}