// v7: Pointing to the correct upstream dependency for recloudstream forks.
// This ensures compatibility and resolves the 401 Unauthorized error.

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
    dependencies {
        // [FIXED v7] Using the recloudstream repository instead of the original LagradOst.
        // This is the standard for projects based on this fork.
        classpath("com.github.recloudstream:cloudstream:master-SNAPSHOT")
    }
}

plugins {
    id("com.android.application").version("8.1.2").apply(false)
    id("com.android.library").version("8.1.2").apply(false)
    kotlin("android").version("1.9.0").apply(false)
}
