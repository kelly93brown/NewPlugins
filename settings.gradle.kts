// v3: Register all your plugin modules here.
// This tells Gradle that "ExampleProvider" is a sub-project it needs to manage.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "CloudstreamPlugins"

// Include your plugins here
include(":ExampleProvider")
// To add a new plugin, create its folder and add a line like this:
// include(":MyNewArabicPlugin")
