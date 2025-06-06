package jinproject.aideo.convention.android

import jinproject.aideo.convention.extension.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidRoomPlugin: Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("androidx.room")
            apply("com.google.devtools.ksp")
        }

        dependencies {
            "implementation"(libs.findLibrary("room.runtime").get())
            "implementation"(libs.findLibrary("room.ktx").get())
            "implementation"(libs.findLibrary("room.common").get())
            "ksp"(libs.findLibrary("room.compiler").get())
            "androidTestImplementation"(libs.findLibrary("room.testing").get())
        }
    }
}