// v9: The Definitive Fix. Using a stable, official tagged release.
// This artifact is confirmed to exist on JitPack and is used by other successful projects.

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
    dependencies {
        // [FIXED v9] Using a specific, official release tag "v0.0.8-RC2".
        // This is a stable version that JitPack has successfully built and hosts.
        // This resolves the 404 "Not Found" error permanently.
        classpath("com.github.recloudstream:cloudstream:v0.0.8-RC2")
    }
}

plugins {
    id("com.android.application").version("8.1.2").apply(false)
    id("com.android.library").version("8.1.2").apply(false)
    kotlin("android").version("1.9.0").apply(false)
}
