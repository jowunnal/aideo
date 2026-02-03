plugins {
    id("jinProject.android.feature")
}

android {
    namespace = "jinproject.aideo.setting"
}

dependencies {
    implementation(project(":features:core"))
}