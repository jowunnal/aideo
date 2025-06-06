package jinproject.aideo.convention.android

import jinproject.aideo.convention.extension.libs
import jinproject.aideo.convention.extension.protobufExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidProtobufPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("com.google.devtools.ksp")
            apply("com.google.protobuf")
        }

        configureProtobuf()

        dependencies {
            "implementation"(libs.findLibrary("protobuf").get())
        }
    }
}

internal fun Project.configureProtobuf() {
    protobufExtension.apply {
        protoc {
            artifact = "com.google.protobuf:protoc:3.25.0"
        }

        generateProtoTasks {
            all().forEach { task ->
                task.builtins {
                    create("java") {
                        option("lite")
                    }
                }
            }
        }
    }
}