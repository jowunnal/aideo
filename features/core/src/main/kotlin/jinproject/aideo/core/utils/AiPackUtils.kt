package jinproject.aideo.core.utils

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.aipacks.AiPackManagerFactory
import com.google.android.play.core.aipacks.AiPackStates
import jinproject.aideo.core.inference.AiModelConfig
import jinproject.aideo.data.BuildConfig
import timber.log.Timber
import java.io.File

fun Context.getPackAssetPath(packName: String): String? {
    if (BuildConfig.DEBUG) {
        val modelsDir = File(filesDir, AiModelConfig.MODELS_ROOT_DIR)
        if (!modelsDir.exists()) modelsDir.mkdirs()
        val fileNames = assets.list(AiModelConfig.MODELS_ROOT_DIR) ?: emptyArray()
        Timber.d("Debug: extracting ${fileNames.size} model files to internal storage")
        for (fileName in fileNames) {
            val outFile = File(modelsDir, fileName)
            if (!outFile.exists()) {
                assets.open("${AiModelConfig.MODELS_ROOT_DIR}/$fileName").use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        return filesDir.absolutePath
    }
    return getAiPackManager().getPackLocation(packName)?.assetsPath()
}

fun Context.isAiPackReady(packName: String): Boolean {
    if (BuildConfig.DEBUG) return true
    return getAiPackManager().getPackLocation(packName) != null
}

fun Context.getAiPackManager() = AiPackManagerFactory.getInstance(this)

fun Context.getAiPackStates(packName: String): Task<AiPackStates> =
    getAiPackManager().getPackStates(listOf(packName))

fun Task<AiPackStates>.getPackStatus(packName: String): Int? =
    result.packStates()[packName]?.status()
