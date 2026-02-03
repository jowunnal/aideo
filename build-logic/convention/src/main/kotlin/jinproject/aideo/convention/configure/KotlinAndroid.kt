package jinproject.aideo.convention.configure

import jinproject.aideo.convention.extension.allowExplicitBackingFields
import jinproject.aideo.convention.extension.androidExtension
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

internal fun Project.configureKotlinAndroid() {
    androidExtension.apply {
        compileSdk = 36

        defaultConfig {
            minSdk = 30
        }

        kotlinExtension.apply {
            jvmToolchain(17)
            allowExplicitBackingFields()
        }
    }
}