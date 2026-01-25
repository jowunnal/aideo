import org.gradle.kotlin.dsl.implementation

plugins {
    id("jinProject.android.feature")
}

android {
    namespace = "jinproject.aideo.gallery"

    flavorDimensions += "htp_version"
    productFlavors {
        create("htp_v69") {
            dimension = "htp_version"
        }
        create("htp_v73") {
            dimension = "htp_version"
        }
        create("htp_v75") {
            dimension = "htp_version"
        }
        create("htp_v79") {
            dimension = "htp_version"
        }
        create("htp_v81") {
            dimension = "htp_version"
        }
    }
}

dependencies {
    implementation(project(":features:core"))
}