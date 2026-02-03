plugins {
    id("jinProject.android.aipack")
}

aiPack {
    packName = "ai_speech_base"

    dynamicDelivery {
        deliveryType = "fast-follow"
    }
}