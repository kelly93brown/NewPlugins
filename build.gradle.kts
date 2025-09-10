// v8: Using a fixed, stable commit hash instead of a volatile SNAPSHOT.
// This is the most robust solution and standard practice for stable builds.

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
    dependencies {
        // [FIXED v8] Replaced "master-SNAPSHOT" with a specific commit hash.
        // This guarantees that we always download the same stable build tools,
        // completely avoiding the JitPack build timeout/failure issue.
        classpath("com.github.recloudstream:cloudstream:6e5e0b0")
    }
}

plugins {
    id("com.android.application").version("8.1.2").apply(false)
    id("com.android.library").version("8.1.2").apply(false)
    kotlin("android").version("1.9.0").apply(false)
}
