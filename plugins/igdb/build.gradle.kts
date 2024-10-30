plugins {
    id("com.google.devtools.ksp") version "2.0.20-1.0.24"
}

dependencies {
    // Kotlin annotation processor
    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:2.0.20-1.0.1")

    // IGDB API client
    implementation("io.github.husnjak:igdb-api-jvm:1.2.0")

    compileOnly("org.slf4j:slf4j-api:2.0.16")
}

tasks.register<Copy>("copyDependencyClasses") {
    dependsOn(tasks.jar)

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(configurations.runtimeClasspath.get().map { project.zipTree(it) }) {
        include("**/*.class")
    }
    from("src/main/resources/MANIFEST.MF") {
        into("META-INF")
    }
    into(layout.buildDirectory.get().asFile.resolve("classes/kotlin/main"))
}

tasks.build {
    dependsOn("copyDependencyClasses")
}