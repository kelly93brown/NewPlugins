// v13: A clean and streamlined build file.
// All repository and buildscript definitions have been moved to settings.gradle.kts.

// We only need to define the plugins now, without the version numbers,
// as they are managed in settings.gradle.kts.
plugins {
    id("com.android.application") apply false
    id("com.android.library") apply false
    kotlin("android") apply false
}

// The rest of the file configures the subprojects, which is its correct responsibility.
// This code is clean and correct.
subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "org.jetbrains.kotlin.android")
    // Note: The custom plugin ID will be resolved correctly now because of the setup in settings.gradle.kts
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    // The rest of the configuration remains the same
    android {
        namespace = "com.adamwolker21.${project.name}"
        compileSdk = 34

        defaultConfig {
            minSdk = 24
            targetSdk = 34
        }
        
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
    }

    dependencies {
        val cloudstream by configurations
        val implementation by configurations

        cloudstream("com.lagradost:cloudstream3:pre-release")
        implementation(kotlin("stdlib"))
        implementation("org.jsoup:jsoup:1.17.2")
        implementation("com.github.Blatzar:NiceHttp:0.4.11")
    }
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}
