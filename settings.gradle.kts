// v4: Re-introducing automatic plugin discovery and centralizing repositories.
// This is the definitive, scalable solution.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Central repository management. This fixes the build error from v3.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "CloudstreamPlugins"

// Automatic plugin inclusion script.
// It finds all subdirectories in the 'plugins' folder and includes them.
file("plugins").listFiles()?.forEach {
    if (it.isDirectory) {
        include(":plugins:${it.name}")
    }
}
