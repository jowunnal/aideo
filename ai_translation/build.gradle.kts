plugins {
    id("jinProject.android.aipack")
}

aiPack {
    packName = "ai_translation"

    dynamicDelivery {
        deliveryType = "on-demand"
    }
}