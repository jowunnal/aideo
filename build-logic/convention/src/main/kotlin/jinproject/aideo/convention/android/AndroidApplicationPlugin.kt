package jinproject.aideo.convention.android

import jinproject.aideo.convention.configure.configureKotlinAndroid
import jinproject.aideo.convention.extension.androidExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

internal class AndroidApplicationPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("com.android.application")
            apply("org.jetbrains.kotlin.android")
            apply("jinProject.android.hilt")
            apply("jinProject.android.compose")
            apply("com.google.gms.google-services")
            apply("com.google.firebase.crashlytics")
            apply("com.github.triplet.play")
        }

        androidExtension.apply {
            defaultConfig {
                vectorDrawables {
                    useSupportLibrary = true
                }
            }
        }

        configureKotlinAndroid()
    }
}
