plugins {
    id("jinProject.android.aipack")
}

aiPack {
    packName = "ai_speech_sensevoice"

    dynamicDelivery {
        deliveryType = "install-time"
    }
}