import org.gradle.kotlin.dsl.implementation

plugins {
    id("jinProject.android.library")
    id("jinProject.android.hilt")
    id("jinProject.android.compose")
}

android {
    namespace = "jinproject.aideo.core"
    compileSdk = 35

}

dependencies {
    implementation(project(":data"))
    api(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    api(libs.bundles.billing)
    //api(project(":whisper_lib"))
    api(libs.bundles.media3)
    implementation(libs.bundles.liteRT)
}