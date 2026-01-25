import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("jinProject.android.application")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(FileInputStream(localPropertiesFile))
    }
}

android {
    namespace = "jinproject.aideo.app"

    signingConfigs {
        create("release") {
            storeFile = file(localProperties.getProperty("signing.storeFile", ""))
            storePassword = localProperties.getProperty("signing.storePassword", "")
            keyAlias = localProperties.getProperty("signing.keyAlias", "")
            keyPassword = localProperties.getProperty("signing.keyPassword", "")
        }
    }

    defaultConfig {
        applicationId = "jinproject.aideo.app"
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false

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
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["ADMOB_APP_ID"] = getLocalKey("adMob.real.appId")
            buildConfigField("String","ADMOB_REWARD_ID",getLocalKey("adMob.real.rewardId"))
            buildConfigField("String", "ADMOB_UNIT_ID", getLocalKey("adMob.real.unitId"))
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    assetPacks += listOf(":ai_speech_base", ":ai_translation", ":ai_speech_whisper")

    bundle {
        deviceTargetingConfig = file("device_targeting_config.xml")
        deviceGroup {
            enableSplit = true
            defaultGroup = "other"
        }
    }

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
    implementation(libs.lifecycle.process)
    coreLibraryDesugaring(libs.android.tools.desugar.jdk.libs)
    implementation(libs.play.ai.delivery)
}

play {
    serviceAccountCredentials.set(
        file(localProperties.getProperty("play.serviceAccountJsonPath", "key/play-service-account.json"))
    )
    track.set("internal")  // internal, alpha, beta, production
    defaultToAppBundles.set(true)
}