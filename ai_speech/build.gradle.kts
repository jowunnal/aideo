plugins {
    id("jinProject.android.aipack")
}

aiPack {
    packName = "ai_speech"

    dynamicDelivery {
        deliveryType = "fast-follow"
    }
}