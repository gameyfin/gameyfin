import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.nio.file.Files

group = "org.gameyfin"
version = "2.1.2"

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
        maven {
            setUrl("https://maven.vaadin.com/vaadin-prereleases/")
        }
    }
}

plugins {
    kotlin("jvm")
}

tasks.named<Jar>("jar") {
    enabled = false
}

subprojects {
    apply(plugin = "java")

    java.sourceCompatibility = JavaVersion.VERSION_21
    java.targetCompatibility = JavaVersion.VERSION_21

    tasks.withType<KotlinJvmCompile> {
        compilerOptions {
            languageVersion = KotlinVersion.KOTLIN_2_2
            apiVersion = KotlinVersion.KOTLIN_2_2
            jvmTarget = JvmTarget.JVM_21
            progressiveMode = true
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }
}

extra.set("pluginDir", rootProject.layout.buildDirectory.get().asFile.resolve("plugins"))

@Suppress("UNCHECKED_CAST")
val updatePackageJsonVersion by tasks.registering {
    group = "build"
    description = "Syncs package.json version with Gradle project version"

    doLast {
        // Read the package.json file
        val packageJson = file("app/package.json")
        val parsedJson = JsonSlurper().parse(packageJson) as MutableMap<String, Any>

        // Update the version field with the Gradle project version
        parsedJson["version"] = project.version.toString()

        // Convert the updated map back to a JSON string
        var stringifiedJson = JsonOutput.toJson(parsedJson)
        stringifiedJson = JsonOutput.prettyPrint(stringifiedJson)

        // Re-adjust indentation to 2 spaces (npm default)
        stringifiedJson = stringifiedJson.replace(Regex("^((?: {4})+)", RegexOption.MULTILINE)) {
            "  ".repeat(it.value.length / 4)
        }

        // Write the updated JSON back to package.json
        Files.write(packageJson.toPath(), stringifiedJson.toByteArray())
    }
}

tasks.named("build") {
    dependsOn(updatePackageJsonVersion)
}