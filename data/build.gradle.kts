import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import com.google.protobuf.gradle.GenerateProtoTask

plugins {
    id("jinProject.android.library")
    id("jinProject.android.hilt")
    id("jinProject.android.protobuf")
    id("jinProject.android.room")
}

android {
    namespace = "jinproject.aideo.data"
    compileSdk = 35

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    sourceSets {
        getByName("androidTest").assets.srcDirs(files("$projectDir/schemas"))
    }

    buildTypes {
        all {
            buildConfigField("String", "PROJECT_NAME", getLocalKey("project.name"))
        }

        debug {
            buildConfigField("String","GOOGLE_STT_ID",getLocalKey("google.test.sttId"))
            buildConfigField("String", "GOOGLE_TRANSLATION_ID", getLocalKey("google.test.translationId"))
        }
        release {
            isMinifyEnabled = true
            consumerProguardFile("proguard-rules.pro")

            buildConfigField("String","GOOGLE_STT_ID",getLocalKey("google.real.sttId"))
            buildConfigField("String", "GOOGLE_TRANSLATION_ID", getLocalKey("google.real.translationId"))
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

fun getLocalKey(propertyKey:String):String{
    return gradleLocalProperties(rootDir, providers).getProperty(propertyKey)
}

dependencies {
    testImplementation(libs.jUnit)
    androidTestImplementation(libs.test.ext)
    androidTestImplementation(libs.test.espresso)

    implementation(libs.coroutines.core)

    implementation(libs.bundles.square)
    implementation(libs.datastore)
}

androidComponents {
    onVariants(selector().all()) { variant ->
        afterEvaluate {
            val protoTask =
                project.tasks.getByName("generate" + variant.name.replaceFirstChar { it.uppercaseChar() } + "Proto") as GenerateProtoTask

            project.tasks.getByName("ksp" + variant.name.replaceFirstChar { it.uppercaseChar() } + "Kotlin") {
                dependsOn(protoTask)
                (this as org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompileTool<*>).setSource(
                    protoTask.outputBaseDir
                )
            }
        }
    }
}
