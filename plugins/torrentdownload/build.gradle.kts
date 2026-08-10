plugins {
    id("com.google.devtools.ksp")
}

repositories {
    maven {
        setUrl("https://dl.frostwire.com/maven")
        content {
            includeGroup("com.frostwire")
        }
    }
}

dependencies {
    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:${rootProject.extra["pf4jKspVersion"]}")

    implementation("com.frostwire:jlibtorrent:${rootProject.extra["frostwireJlibTorrentVersion"]}")
    implementation("com.frostwire:jlibtorrent-windows:${rootProject.extra["frostwireJlibTorrentVersion"]}")
    implementation("com.frostwire:jlibtorrent-macosx-x86_64:${rootProject.extra["frostwireJlibTorrentVersion"]}")
    implementation("com.frostwire:jlibtorrent-macosx-arm64:${rootProject.extra["frostwireJlibTorrentVersion"]}")
    implementation("com.frostwire:jlibtorrent-linux-x86_64:${rootProject.extra["frostwireJlibTorrentVersion"]}")
    implementation("com.frostwire:jlibtorrent-linux-arm64:${rootProject.extra["frostwireJlibTorrentVersion"]}")
}

// Extract native libraries from jlibtorrent JARs for local debugging
tasks.register<Copy>("extractNativeLibraries") {
    description = "Extracts the native libraries"
    group = JavaBasePlugin.DOCUMENTATION_GROUP

    dependsOn(tasks.named("compileKotlin"))

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(configurations.runtimeClasspath.get().map { project.zipTree(it) }) {
        include("lib/**")
    }
    into(layout.buildDirectory.get().asFile.resolve("classes/kotlin/main"))
}

tasks.named("classes") {
    dependsOn("extractNativeLibraries")
}

tasks.named("test") {
    dependsOn("extractNativeLibraries")
}
