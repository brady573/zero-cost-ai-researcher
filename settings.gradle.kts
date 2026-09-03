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

rootProject.name = "ZeroCostAIResearcher"
include(":app")

val llamaAndroidDir = file("third_party/llama.cpp/examples/llama.android/lib")
if (llamaAndroidDir.exists()) {
    include(":llamaAndroid")
    project(":llamaAndroid").projectDir = llamaAndroidDir
}
