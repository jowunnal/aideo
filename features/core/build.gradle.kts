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
            abiFilters.add("arm64-v8a")  // ← arm64-v8a만 빌드
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

    flavorDimensions += "htp_version"
    productFlavors {
        create("htp_v69") {
            dimension = "htp_version"
        }
        create("htp_v73") {
            dimension = "htp_version"
        }
        create("htp_v75") {
            dimension = "htp_version"
        }
        create("htp_v79") {
            dimension = "htp_version"
        }
        create("htp_v81") {
            dimension = "htp_version"
        }
    }
}

dependencies {
    implementation(project(":data"))
    api(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    api(libs.bundles.billing)
    api(libs.bundles.media3)
    implementation(libs.executorch.android)
    api(libs.google.ads.interactive.media)
}