// v14: Replicating the exact, working buildscript structure from successful projects.
import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        // Using the same dependency versions as the reference projects
        classpath("com.android.tools.build:gradle:8.1.3")
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) =
    extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) =
    extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/adamwolker21/TestPlugins")
    }

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
