pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://plugins.gradle.org/m2/") }
    }
    includeBuild("build-logic")
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Aideo"
include(":app")
include(":features")
include(":design")
include(":data")
include(":features:gallery")
include(":features:core")
include(":features:player")
include(":features:library")
include(":features:setting")
include(":ai_speech_base")
include(":ai_speech_whisper")
include(":ai_speech_sensevoice")
include(":ai_translation")
include(":htp_v69_sm8475")
include(":htp_v73_sm8550")
include(":htp_v75")
include(":htp_v79")
include(":htp_v81")
include(":htp_v69_sm8450")
