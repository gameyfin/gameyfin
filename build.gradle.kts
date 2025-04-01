import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

plugins {
    kotlin("jvm")
}

subprojects {
    apply(plugin = "java")

    version = "2.0.0-SNAPSHOT"

    java.sourceCompatibility = JavaVersion.VERSION_21
    java.targetCompatibility = JavaVersion.VERSION_21

    tasks.withType<KotlinJvmCompile> {
        compilerOptions {
            languageVersion = KotlinVersion.KOTLIN_2_1
            apiVersion = KotlinVersion.KOTLIN_2_1
            jvmTarget = JvmTarget.JVM_21
            progressiveMode = true
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }
}

extra.set("pluginDir", rootProject.layout.buildDirectory.get().asFile.resolve("plugins"))