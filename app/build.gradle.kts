import com.android.build.api.dsl.AaptOptions
import com.android.build.api.dsl.AndroidResources
import com.android.build.api.dsl.Packaging
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    id("jinProject.android.application")
}

android {
    namespace = "jinproject.aideo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "jinproject.aideo.app"
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            manifestPlaceholders["ADMOB_APP_ID"] = getLocalKey("adMob.test.appId")
            buildConfigField("String","ADMOB_REWARD_ID",getLocalKey("adMob.test.rewardId"))
            buildConfigField("String", "ADMOB_UNIT_ID", getLocalKey("adMob.test.unitId"))
            extra.set("alwaysUpdateBuildId", true)
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["ADMOB_APP_ID"] = getLocalKey("adMob.real.appId")
            buildConfigField("String","ADMOB_REWARD_ID",getLocalKey("adMob.real.rewardId"))
            buildConfigField("String", "ADMOB_UNIT_ID", getLocalKey("adMob.real.unitId"))
        }
    }

    buildFeatures {
        buildConfig = true
    }

    android {
        androidResources {
            noCompress += "tflite"
        }
    }
}

fun getLocalKey(propertyKey:String):String{
    return gradleLocalProperties(rootDir, providers).getProperty(propertyKey)
}

dependencies {
    implementation(project(":data"))
    implementation(project(":design"))
    implementation(project(":features:gallery"))
    implementation(project(":features:player"))
    implementation(project(":features:core"))

    implementation(libs.google.gms.services.ads)
    implementation(libs.bundles.playInAppUpdate)
    implementation(platform(libs.firebase.bom))
    implementation(libs.bundles.firebase)
}