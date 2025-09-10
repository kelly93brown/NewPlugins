// v14: Simplifying back to the basics. This file's only job is to include the plugins.
rootProject.name = "TestPlugins"

file("plugins").listFiles()?.forEach {
    if (it.isDirectory) {
        include(":plugins:${it.name}")
    }
}
