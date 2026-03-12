package jinproject.aideo.convention.configure

import jinproject.aideo.convention.configure.VersionConfig.COMPILE_SDK
import jinproject.aideo.convention.configure.VersionConfig.JVM
import jinproject.aideo.convention.configure.VersionConfig.MIN_SDK
import jinproject.aideo.convention.extension.allowExplicitBackingFields
import jinproject.aideo.convention.extension.androidExtension
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

internal fun Project.configureKotlinAndroid() {
    androidExtension.apply {
        compileSdk = COMPILE_SDK

        defaultConfig {
            minSdk = MIN_SDK
        }

        kotlinExtension.apply {
            jvmToolchain(JVM)
            allowExplicitBackingFields()
        }
    }
}

object VersionConfig {
    const val COMPILE_SDK = 36
    const val MIN_SDK = 30
    const val JVM = 17
}