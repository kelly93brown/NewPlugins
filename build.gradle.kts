// v10: The correct, centralized build structure inspired by successful repositories.
// This single file configures all subprojects (providers) automatically.

import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        // JitPack repository which contains our tools and dependencies
        maven("https://jitpack.io")
    }

    dependencies {
        // These are the build tools.
        classpath("com.android.tools.build:gradle:8.1.2")
        // This is the essential CloudStream gradle plugin that builds the providers.
        classpath("com.github.recloudstream:gradle:v0.0.8")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
    }
}

// Apply repositories to all projects, including the root and all subprojects.
allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// Helper functions to easily configure android and cloudstream blocks in subprojects.
fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) =
    extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) =
    extensions.getByName<BaseExtension>("android").configuration()


// This is the core of the new structure.
// The 'subprojects' block applies the following configuration to EVERY folder
// inside the 'plugins' directory automatically.
subprojects {
    // Apply the necessary plugins for each provider.
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    // Configure the CloudStream-specific settings for each provider.
    cloudstream {
        // Automatically set the repository URL from the GitHub environment.
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/adamwolker21/TestPlugins")
    }

    // Configure the Android-specific settings for each provider.
    android {
        // It's good practice to set a base namespace.
        namespace = "com.adamwolker21"

        defaultConfig {
            minSdk = 21
            // Use compileSdk 34 as it's the most stable and widely used version for now.
            compileSdkVersion(34)
            targetSdk = 34
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8)
            }
        }
    }

    // Define the common dependencies that most providers will need.
    // This avoids having to declare them in every single provider.
    dependencies {
        val cloudstream by configurations
        val implementation by configurations

        // Stubs for all CloudStream classes, allowing you to code the provider.
        cloudstream("com.lagradost:cloudstream3:pre-release")

        // Essential libraries for web scraping and data handling.
        implementation(kotlin("stdlib"))
        implementation("org.jsoup:jsoup:1.17.2") // HTML parser
        implementation("com.github.Blatzar:NiceHttp:0.4.11") // Powerful HTTP client
    }
}

// A task to clean the build directory.
task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
