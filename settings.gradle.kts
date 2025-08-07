pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        kotlin("android") version "1.9.22"
        id("com.android.application") version "8.12.0"
        id("com.android.library") version "8.12.0"
        kotlin("jvm") version "1.9.10"
        kotlin("plugin.serialization") version "1.9.10"
        id("io.ktor.plugin") version "2.3.4"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Melody"
include(":app")
include(":backend")
 