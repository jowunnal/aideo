plugins {
    id("jinProject.android.library")
    id("jinProject.android.compose")
}

android {
    namespace = "jinproject.aideo.design"
    compileSdk = 35
}

dependencies {
    implementation(libs.coil)
    implementation(libs.airbnb.android.lottie.compose)
}