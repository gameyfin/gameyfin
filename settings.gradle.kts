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
        kotlin("jvm") version extra["kotlinVersion"] as String
        kotlin("plugin.spring") version extra["kotlinVersion"] as String
        kotlin("plugin.jpa") version extra["kotlinVersion"] as String
        kotlin("plugin.serialization") version extra["kotlinVersion"] as String
    }
}


include(":plugin-api")
include(":gameyfin")

include(":plugins")

include(":plugins:igdb")
include(":plugins:steam")
include(":plugins:steamgriddb")