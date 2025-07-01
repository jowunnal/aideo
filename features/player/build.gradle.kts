plugins {
    id("jinProject.android.feature")
}

android {
    namespace = "jinproject.aideo.player"
    compileSdk = 35
}

dependencies {
    implementation(project(":features:core"))
}