// v24: The real and final fix. Including the subprojects is the critical step.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "TestPlugins"

// This is the most important line. It tells Gradle that "ExampleProvider" is a subproject.
include(":ExampleProvider")

// If you add more plugins in the future, add them here like this:
// include(":AnotherProvider")
// include(":SomeOtherPlugin")
