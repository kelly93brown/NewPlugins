// v5: Define the plugin's identity.
// This block tells Gradle that this module is an Android Library
// and that it uses the CloudStream plugin system. This resolves all
// the "Unresolved reference" errors.

plugins {
    id("com.android.library")
    kotlin("android")
    // This is the magic line that enables all CloudStream features
    id("com.lagradost.cloudstream3.plugin")
}

// The CloudStream plugin block. Now Gradle understands what this is.
cloudstream {
    // All of these will be resolved now.
    authors = listOf("Cloudburst", "Luna712")
    versionCode = 1
    // The language of the provider.
    language = "en"
    // The name of the provider.
    name = "Example Provider"
    // The type of content the provider hosts.
    tvTypes = listOf("Movie")
    // If the provider requires the user to install a separate app for it to work.
    requiresResources = true
    // The icon of the provider.
    iconUrl = "https://upload.wikimedia.org/wikipedia/commons/2/2f/Kordguene_Logo.png"
}

// The Android configuration block. Gradle also understands this now.
android {
    // The namespace of the provider.
    namespace = "com.example.exampleprovider"
    // The minimum SDK version the provider supports.
    minSdk = 21
    // The target SDK version the provider targets.
    compileSdk = 34

    // Don't change this.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // Don't change this.
    buildFeatures {
        viewBinding = true
    }
}

// The dependencies block. 'implementation' is now a known function.
dependencies {
    // Don't change this.
    implementation(project(":app"))
}
