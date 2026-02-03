package jinproject.aideo.convention.android

import com.android.build.api.dsl.DynamicFeatureExtension
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
            compileSdk = 36

            defaultConfig {
                minSdk = 30
            }
        }
    }
}
