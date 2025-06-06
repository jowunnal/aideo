plugins {
    id("jinProject.android.feature")
}

android {
    namespace = "jinproject.aideo.gallery"
    compileSdk = 35
}

dependencies {
    api(project(":features:core"))
}