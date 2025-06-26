plugins {
    id("jinProject.android.feature")
}

android {
    namespace = "jinproject.aideo.player"
    compileSdk = 35
}

dependencies {
    api(project(":features:core"))

    implementation(libs.bundles.exoplayer)
}