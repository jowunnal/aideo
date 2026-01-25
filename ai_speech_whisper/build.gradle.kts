plugins {
    id("jinProject.android.aipack")
}

aiPack {
    packName = "ai_speech_whisper"

    dynamicDelivery {
        deliveryType = "on-demand"
    }
}