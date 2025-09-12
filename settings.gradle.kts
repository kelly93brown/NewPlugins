// هذا الملف ضروري جداً لتعريف Gradle بمكان البحث عن الإضافات
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
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

// قم بتضمين مجلدات الإضافات الخاصة بك هنا
// بناءً على هيكل مشروعك، لديك على الأقل هذا المجلد
include(":ExampleProvider")
