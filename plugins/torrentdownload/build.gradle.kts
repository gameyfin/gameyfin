val libtorrent4jVersion = "2.1.0-38"

plugins {
    id("com.google.devtools.ksp")
}

repositories {
    maven { setUrl("https://jitpack.io") }
    maven { setUrl("https://repository.jboss.org") }
}

dependencies {

    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:${rootProject.extra["pf4jKspVersion"]}")

    // libtorrent4j
    implementation("org.libtorrent4j:libtorrent4j:$libtorrent4jVersion")
    implementation("org.libtorrent4j:libtorrent4j-linux:${libtorrent4jVersion}")
    implementation("org.libtorrent4j:libtorrent4j-windows:${libtorrent4jVersion}")
    implementation("org.libtorrent4j:libtorrent4j-macos:${libtorrent4jVersion}")
}

// Extract native libraries from jlibtorrent JARs for local debugging
tasks.register<Copy>("extractNativeLibraries") {
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
