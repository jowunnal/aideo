package jinproject.aideo.convention.kotlin

import jinproject.aideo.convention.kotlin.configure.configureKotlinJVM
import jinproject.aideo.convention.kotlin.configure.configureKotlinTest
import org.gradle.api.Plugin
import org.gradle.api.Project

class KotlinLibraryPlugin: Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.kotlin.jvm")

            configureKotlinJVM()
            configureKotlinTest()
        }
    }
}