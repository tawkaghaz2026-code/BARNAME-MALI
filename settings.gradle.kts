pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.1.0"
        id("org.jetbrains.kotlin.jvm") version "2.1.0"
        id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
        id("com.google.devtools.ksp") version "2.1.0-1.0.29"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BarnameMali"

include(":core")
// The Android module needs the Android SDK. Set -PcoreOnly=true to build only :core.
if (providers.gradleProperty("coreOnly").orNull != "true") {
    include(":app")
}
