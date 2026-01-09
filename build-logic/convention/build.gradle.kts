plugins {
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
}

dependencies {
    implementation(libs.gradle.android)
    implementation(libs.gradle.kotlin)
    implementation(libs.gradle.kotlin.compose)
    implementation(libs.gradle.hilt)
    implementation(libs.gradle.google.gms.google.services)
    implementation(libs.gradle.google.firebase.crashlytics)
    implementation(libs.gradle.google.devtools.ksp)
    implementation(libs.gradle.protobuf)
    implementation(libs.gradle.kotlin.serialization)
    implementation(libs.gradle.room)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "jinProject.android.application"
            implementationClass = "jinproject.aideo.convention.android.AndroidApplicationPlugin"
        }
        register("androidLibrary") {
            id = "jinProject.android.library"
            implementationClass = "jinproject.aideo.convention.android.AndroidLibraryPlugin"
        }
        register("androidHilt") {
            id = "jinProject.android.hilt"
            implementationClass = "jinproject.aideo.convention.android.AndroidHiltPlugin"
        }
        register("androidCompose") {
            id = "jinProject.android.compose"
            implementationClass = "jinproject.aideo.convention.android.AndroidComposePlugin"
        }
        register("androidFeature") {
            id = "jinProject.android.feature"
            implementationClass = "jinproject.aideo.convention.android.AndroidFeaturePlugin"
        }
        register("androidProtobuf") {
            id = "jinProject.android.protobuf"
            implementationClass = "jinproject.aideo.convention.android.AndroidProtobufPlugin"
        }
        register("androidRoom") {
            id = "jinProject.android.room"
            implementationClass = "jinproject.aideo.convention.android.AndroidRoomPlugin"
        }
        register("kotlinLibrary") {
            id = "jinProject.kotlin.library"
            implementationClass = "jinproject.aideo.convention.kotlin.KotlinLibraryPlugin"
        }
        register("AiPack") {
            id = "jinProject.android.aipack"
            implementationClass = "jinproject.aideo.convention.android.AndroidAiPackPlugin"
        }
    }
}
