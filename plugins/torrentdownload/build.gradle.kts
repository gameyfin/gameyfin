plugins {
    id("com.google.devtools.ksp")
}

repositories {
    maven { setUrl("https://jitpack.io") }
    maven { setUrl("https://repository.jboss.org") }
}

dependencies {

    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:${rootProject.extra["pf4jKspVersion"]}")

    // Torrent tracker & seeder
    implementation("com.github.mpetazzoni:ttorrent:ttorrent-2.0") {
        exclude(group = "org.slf4j")
    }

    // Torrent file builder
    implementation("com.github.atomashpolskiy:bt-core:1.10") {
        exclude(group = "org.slf4j")
    }
}