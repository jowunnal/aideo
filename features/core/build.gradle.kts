import com.android.build.api.dsl.LibraryProductFlavor
import org.gradle.kotlin.dsl.implementation

plugins {
    id("jinProject.android.library")
    id("jinProject.android.hilt")
    id("jinProject.android.compose")
}

android {
    namespace = "jinproject.aideo.core"

    ndkVersion = "29.0.13599879"

    defaultConfig {
        ndk {
            abiFilters.add("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.0.2"
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation(project(":data"))
    implementation(project(":design"))
    api(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    api(libs.bundles.billing)
    api(libs.bundles.media3)
    implementation(libs.executorch.android)
    api(libs.google.ads.interactive.media)
    implementation(libs.bundles.mlKit)
    api(libs.play.ai.delivery)
}