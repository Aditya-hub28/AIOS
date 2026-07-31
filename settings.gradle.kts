pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://alphacephei.com/maven/") }
        maven { url = uri("https://maven.alphacephei.com") }
        maven { url = uri("https://jitpack.io") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://alphacephei.com/maven/") }
        maven { url = uri("https://maven.alphacephei.com") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "VoiceControl"
include(":app")
