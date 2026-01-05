import org.gradle.kotlin.dsl.implementation

plugins {
    id("jinProject.android.library")
    id("jinProject.android.hilt")
    id("jinProject.android.compose")
}

android {
    namespace = "jinproject.aideo.core"
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