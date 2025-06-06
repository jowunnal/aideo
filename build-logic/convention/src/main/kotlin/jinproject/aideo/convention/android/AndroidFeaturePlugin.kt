package jinproject.aideo.convention.android

import jinproject.aideo.convention.configure.configureAndroidTest
import jinproject.aideo.convention.extension.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

internal class AndroidFeaturePlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("jinProject.android.library")
            apply("jinProject.android.hilt")
            apply("jinProject.android.compose")
        }

        configureAndroidTest()

        dependencies {
            "implementation"(project(":data"))
            "implementation"(project(":design"))

            "implementation"(libs.findLibrary("hilt.navigation.compose").get())
            "implementation"(libs.findBundle("lifecycle").get())
            "implementation"(libs.findLibrary("kotlinx-collections-immutable").get())
            "implementation"(libs.findLibrary("coil").get())
        }
    }
}
