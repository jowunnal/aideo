import org.gradle.kotlin.dsl.implementation

plugins {
    id("jinProject.android.feature")
}

android {
    namespace = "jinproject.aideo.gallery"
    compileSdk = 35
}

dependencies {
    implementation(project(":features:core"))

    implementation(libs.bundles.tensorflow.lite)
}