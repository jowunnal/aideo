plugins {
    id("jinProject.android.aipack")
}

android {
    androidResources {
        noCompress += listOf<String>("tflite", "so", "bin", "pb" )
    }
}

aiPack {
    packName = "ai_pack"

    dynamicDelivery {
        deliveryType = "install-time"
    }
}