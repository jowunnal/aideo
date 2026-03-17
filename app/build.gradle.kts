import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    id("jinProject.android.application")
}

android {
    namespace = "jinproject.aideo.app"

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(getLocalKey("signing.storeFile"))
            storePassword = getLocalKey("signing.storePassword")
            keyAlias = getLocalKey("signing.keyAlias")
            keyPassword = getLocalKey("signing.keyPassword")
        }
    }

    defaultConfig {
        applicationId = "jinproject.aideo.app"
        targetSdk = 36
        versionCode = 29
        versionName = "1.0.5"
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
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", getLocalKey("adMob.test.interstitialId"))
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
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", getLocalKey("adMob.real.interstitialId"))
            buildConfigField("String", "ADMOB_UNIT_ID", getLocalKey("adMob.real.unitId"))
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    sourceSets {
        getByName("debug") {
            assets.srcDirs(
                "../ai_speech_base/src/main/assets",
                "../ai_speech_whisper/src/main/assets",
                layout.buildDirectory.dir("generated/sensevoice_debug_assets").get().asFile,
                "../ai_translation/src/main/assets",
                "../htp_v73_sm8550/src/main/assets",
            )
        }
    }

    dynamicFeatures += setOf(
        ":htp_v69_sm8475", ":htp_v69_sm8450", ":htp_v73_sm8550",
        ":htp_v75", ":htp_v79", ":htp_v81",
    )

    assetPacks += listOf(
        ":ai_speech_base", ":ai_translation",
        ":ai_speech_whisper", ":ai_speech_sensevoice"
    )

    bundle {
        deviceTargetingConfig = file("device_targeting_config.xml")
        deviceGroup {
            enableSplit = true
            defaultGroup = "other"
        }
    }
}

val copySenseVoiceDebugAssets = tasks.register<Copy>("copySenseVoiceDebugAssets") {
    from("$rootDir/ai_speech_sensevoice/src/main/assets/models#group_base")
    into(layout.buildDirectory.dir("generated/sensevoice_debug_assets/models"))
}
afterEvaluate {
    tasks.named("mergeDebugAssets") {
        dependsOn(copySenseVoiceDebugAssets)
    }
}

fun getLocalKey(propertyKey: String): String {
    return gradleLocalProperties(rootDir, providers).getProperty(propertyKey)
}

dependencies {
    implementation(project(":data"))
    implementation(project(":design"))
    implementation(project(":features:gallery"))
    implementation(project(":features:player"))
    implementation(project(":features:core"))
    implementation(project(":features:library"))
    implementation(project(":features:setting"))

    implementation(libs.google.gms.services.ads)
    implementation(libs.bundles.playInAppUpdate)
    implementation(platform(libs.firebase.bom))
    implementation(libs.bundles.firebase)
    implementation(libs.lifecycle.process)
    coreLibraryDesugaring(libs.android.tools.desugar.jdk.libs)
    implementation(libs.play.ai.delivery)
    implementation(libs.play.feature.delivery.ktx)
}

play {
    serviceAccountCredentials.set(
        rootProject.file(getLocalKey("play.serviceAccountJsonPath"))
    )
    defaultToAppBundles.set(true)
}