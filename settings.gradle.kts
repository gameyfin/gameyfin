rootProject.name = "Gameyfin"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven(url = "https://maven.vaadin.com/vaadin-prereleases")
    }
    plugins {
        id("com.vaadin") version extra["vaadinVersion"] as String
        id("org.springframework.boot") version extra["springBootVersion"] as String
        id("io.spring.dependency-management") version extra["springDependencyManagementVersion"] as String
        id("com.google.devtools.ksp") version extra["kspVersion"] as String
        id("org.sonarqube") version extra["sonarVersion"] as String
        id("com.vanniktech.maven.publish") version extra["mavenPublishVersion"] as String
        kotlin("jvm") version extra["kotlinVersion"] as String
        kotlin("plugin.spring") version extra["kotlinVersion"] as String
        kotlin("plugin.jpa") version extra["kotlinVersion"] as String
        kotlin("plugin.serialization") version extra["kotlinVersion"] as String
        kotlin("kapt") version "2.4.21"
    }
}


include(":plugin-api")
include(":app")

// Standalone plugin checkouts participate only in an explicit development composite.
providers.gradleProperty("pluginDevDir").orNull?.let { directory ->
    val root = file(directory)
    require(root.isDirectory) { "pluginDevDir must be a directory: $root" }
    // Register this build's API for substitution in every included plugin build.
    includeBuild(".") {
        dependencySubstitution {
            substitute(module("org.gameyfin:plugin-api")).using(project(":plugin-api"))
        }
    }
    root.listFiles().orEmpty()
        .filter { it.isDirectory && it.resolve("settings.gradle.kts").isFile }
        .sortedBy { it.name }
        .forEach { includeBuild(it) }
}
