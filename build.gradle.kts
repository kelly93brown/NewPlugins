// v4: Simplified root build script.
// The conflicting 'subprojects' block has been removed, as repository
// configuration is now handled entirely by settings.gradle.kts.

plugins {
    id("com.android.application").version("8.1.2").apply(false)
    id("com.android.library").version("8.1.2").apply(false)
    kotlin("android").version("1.9.0").apply(false)
}

// No 'subprojects' block needed anymore!
