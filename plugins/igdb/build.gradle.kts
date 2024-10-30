plugins {
    id("com.google.devtools.ksp") version "2.0.20-1.0.24"
}

dependencies {
    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:2.0.20-1.0.1")

    // IGDB API client
    implementation("io.github.husnjak:igdb-api-jvm:1.2.0")
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