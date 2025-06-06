package jinproject.aideo.core

import androidx.annotation.DrawableRes

interface TopLevelRoute {
    @get:DrawableRes
    val icon: Int

    @get:DrawableRes
    val iconClicked: Int
}
