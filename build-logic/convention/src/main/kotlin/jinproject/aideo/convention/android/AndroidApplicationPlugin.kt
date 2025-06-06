package jinproject.aideo.convention.android

import jinproject.aideo.convention.configure.configureKotlinAndroid
import jinproject.aideo.convention.extension.androidExtension
import jinproject.aideo.convention.extension.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

internal class AndroidApplicationPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("com.android.application")
            apply("org.jetbrains.kotlin.android")
            apply("jinProject.android.hilt")
            apply("jinProject.android.compose")
            apply("com.google.gms.google-services")
            apply("com.google.firebase.crashlytics")
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
