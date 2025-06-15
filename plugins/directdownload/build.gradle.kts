plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:${rootProject.extra["pf4jKspVersion"]}")

    implementation("commons-io:commons-io:2.19.0")
}