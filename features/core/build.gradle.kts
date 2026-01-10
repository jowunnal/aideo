import com.android.build.api.dsl.LibraryProductFlavor
import org.gradle.kotlin.dsl.implementation

plugins {
    id("jinProject.android.library")
    id("jinProject.android.hilt")
    id("jinProject.android.compose")
}

android {
    namespace = "jinproject.aideo.core"

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
    implementation(libs.bundles.liteRT)
    implementation(libs.executorch.android)
    api(libs.google.ads.interactive.media)
}