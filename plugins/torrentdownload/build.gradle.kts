plugins {
    id("com.google.devtools.ksp")
}

repositories {
    maven { setUrl("https://jitpack.io") }
    maven { setUrl("https://repository.jboss.org") }
}

dependencies {

    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:${rootProject.extra["pf4jKspVersion"]}")

    // libtorrent4j - Complete BitTorrent implementation (creation, tracking, seeding)
    implementation("org.libtorrent4j:libtorrent4j:2.1.0-29")
}