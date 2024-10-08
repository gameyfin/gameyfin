import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

allprojects {
    repositories {
        mavenCentral()
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
            apiVersion = KotlinVersion.KOTLIN_2_2
            languageVersion = KotlinVersion.KOTLIN_2_2
            jvmTarget = JvmTarget.JVM_21
            progressiveMode = true
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }
}
