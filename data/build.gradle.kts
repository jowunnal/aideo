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

    defaultConfig {
        buildConfigField("String","OPENAI_APIKEY",getLocalKey("openAI.apiKey"))
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            consumerProguardFile("proguard-rules.pro")
        }
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