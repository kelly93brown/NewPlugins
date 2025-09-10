// v6: Providing the custom CloudStream plugin to all sub-projects.
// This is the final key to connecting your project with the CloudStream build system.

// This 'buildscript' block is executed first. It tells Gradle where to find
// the custom plugin code needed to build the providers.
buildscript {
    repositories {
        google()
        mavenCentral()
        // The CloudStream plugin is hosted on JitPack
        maven { url = uri("https://jitpack.io") }
    }
    dependencies {
        // This is the classpath to the custom plugin itself.
        // It makes the "com.lagradost.cloudstream3.plugin" ID available.
        classpath("com.github.LagradOst:CloudStream-3:master-SNAPSHOT")
    }
}

plugins {
    id("com.android.application").version("8.1.2").apply(false)
    id("com.android.library").version("8.1.2").apply(false)
    kotlin("android").version("1.9.0").apply(false)
}
