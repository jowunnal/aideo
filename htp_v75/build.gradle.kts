plugins {
    id("jinProject.android.dynamic-feature")
}

android {
    namespace = "jinproject.aideo.htp_v75"

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation(project(":app"))
}
