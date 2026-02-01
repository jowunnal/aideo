plugins {
    id("jinProject.android.feature")
}

android {
    namespace = "jinproject.aideo.library"
}

dependencies {
    implementation(project(":features:core"))
}
