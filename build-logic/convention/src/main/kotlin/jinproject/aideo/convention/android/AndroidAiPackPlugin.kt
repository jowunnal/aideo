package jinproject.aideo.convention.android

import jinproject.aideo.convention.extension.libraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidAiPackPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        with(pluginManager) {
            apply("com.android.library")
            apply("com.android.ai-pack")

        }

        libraryExtension.apply {
            namespace = "jinproject.aideo.aipack"
            compileSdk = 36
        }
    }
}
