plugins {
    id("jinProject.android.feature")
}

android {
    namespace = "jinproject.aideo.player"
}

dependencies {
    implementation(project(":features:core"))
}