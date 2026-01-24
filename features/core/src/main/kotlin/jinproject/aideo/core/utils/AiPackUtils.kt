package jinproject.aideo.core.utils

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.aipacks.AiPackManagerFactory
import com.google.android.play.core.aipacks.AiPackStates

fun Context.getPackAssetPath(packName: String): String? {
    return getAiPackManager().getPackLocation(packName)?.assetsPath()
}

fun Context.getAiPackManager() = AiPackManagerFactory.getInstance(this)

fun Context.getAiPackStates(packName: String): Task<AiPackStates> = getAiPackManager().getPackStates(listOf(packName))

fun  Task<AiPackStates>.getPackStatus(packName: String): Int? = result.packStates()[packName]?.status()