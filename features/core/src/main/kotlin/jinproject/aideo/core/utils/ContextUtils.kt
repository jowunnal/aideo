package jinproject.aideo.core.utils

import android.content.Context
import com.google.android.play.core.aipacks.AiPackManagerFactory

fun Context.getPackAssetPath(packName: String): String? {
    return getAiPackManager().getPackLocation(packName)?.assetsPath()
}

fun Context.getAiPackManager() = AiPackManagerFactory.getInstance(this)