val jlibtorrentVersion = "2.0.12.7"

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

    implementation("com.frostwire:jlibtorrent:$jlibtorrentVersion")
    implementation("com.frostwire:jlibtorrent-windows:${jlibtorrentVersion}")
    implementation("com.frostwire:jlibtorrent-macosx-x86_64:${jlibtorrentVersion}")
    implementation("com.frostwire:jlibtorrent-macosx-arm64:${jlibtorrentVersion}")
    implementation("com.frostwire:jlibtorrent-linux-x86_64:${jlibtorrentVersion}")
    implementation("com.frostwire:jlibtorrent-linux-arm64:${jlibtorrentVersion}")
}