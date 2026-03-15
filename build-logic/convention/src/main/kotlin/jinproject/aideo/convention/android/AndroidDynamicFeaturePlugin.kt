package jinproject.aideo.convention.android

import com.android.build.api.dsl.DynamicFeatureExtension
import jinproject.aideo.convention.configure.VersionConfig.COMPILE_SDK
import jinproject.aideo.convention.configure.VersionConfig.MIN_SDK
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidDynamicFeaturePlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        with(pluginManager) {
            apply("com.android.dynamic-feature")
            apply("org.jetbrains.kotlin.android")
        }

        extensions.configure<DynamicFeatureExtension> {
            compileSdk = COMPILE_SDK

            defaultConfig {
                minSdk = MIN_SDK
            }
        }
    }
}
