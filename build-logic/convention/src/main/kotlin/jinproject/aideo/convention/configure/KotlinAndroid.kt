package jinproject.aideo.convention.configure

import jinproject.aideo.convention.extension.allowExplicitBackingFields
import jinproject.aideo.convention.extension.androidExtension
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

internal fun Project.configureKotlinAndroid() {
    androidExtension.apply {
        compileSdk = 35

        defaultConfig {
            minSdk = 26
        }

        kotlinExtension.apply {
            jvmToolchain(17)
            allowExplicitBackingFields()
        }
    }
}