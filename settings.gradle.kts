// v13: The Absolute Final Structure.
// Centralizing all repository and plugin management as required by modern Gradle.

// This block tells Gradle where to find the plugins themselves (like the Android plugin).
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal() // The official portal for Gradle plugins
    }
}

// This block tells Gradle where to find project dependencies (like Jsoup, NiceHttp, etc.)
// It also enforces the rule that ONLY these repositories can be used.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "TestPlugins"

// This smart script automatically includes any folder inside "plugins" as a subproject.
// This part remains unchanged because it works perfectly.
file("plugins").listFiles()?.forEach {
    if (it.isDirectory) {
        include(":plugins:${it.name}")
    }
}
