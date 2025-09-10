// v3: Central build script for the entire project.
// This applies common settings to all sub-projects (plugins)
// to ensure consistency and avoid conflicts, just as you observed.

plugins {
    // These plugins are applied to the root project but not to sub-projects directly.
    // 'apply(false)' means they are made available for sub-projects to apply.
    id("com.android.application").version("8.1.2").apply(false)
    id("com.android.library").version("8.1.2").apply(false)
    kotlin("android").version("1.9.0").apply(false)
}

// This block configures all sub-projects (your plugins)
subprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
