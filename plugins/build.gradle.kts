plugins {
    kotlin("jvm")
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        compileOnly(project(":plugin-api"))
        implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")
    }

    tasks.jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        isZip64 = true
        archiveBaseName.set("plugin-${project.name}")

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
}