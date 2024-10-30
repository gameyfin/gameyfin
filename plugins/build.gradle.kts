val pluginDir: File by rootProject.extra

plugins {
    kotlin("jvm")
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(project(":plugin-api"))
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

    tasks.register<Copy>("assemblePlugin") {
        from(project.tasks.jar)
        into(pluginDir)
    }
}

tasks.register<Copy>("assemblePlugins") {
    dependsOn(subprojects.map { it.tasks.named("assemblePlugin") })
}

tasks {
    "build" {
        dependsOn(named("assemblePlugins"))
    }
}