plugins {
    id("jinProject.android.aipack")
}

aiPack {
    packName = "ai_pack"

    dynamicDelivery {
        deliveryType = "install-time"
    }
}