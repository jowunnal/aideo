import org.gradle.kotlin.dsl.implementation

plugins {
    id("jinProject.android.feature")
}

android {
    namespace = "jinproject.aideo.gallery"
}

dependencies {
    implementation(project(":features:core"))
}