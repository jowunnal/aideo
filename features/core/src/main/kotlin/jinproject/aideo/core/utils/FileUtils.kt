package jinproject.aideo.core.utils

import android.content.Context
import android.content.res.AssetManager
import jinproject.aideo.core.inference.AiModelConfig
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.collections.contains

fun extractQnnStubsToInternalStorage(context: Context): String {
    val stubDir = File(context.filesDir, AiModelConfig.QNN_STUBS_ROOT_DIR)
    if (!stubDir.exists()) {
        stubDir.mkdirs()
    }

    val assetManager = context.assets
    val stubFiles = assetManager.list(AiModelConfig.QNN_STUBS_ROOT_DIR) ?: emptyArray()

    for (fileName in stubFiles) {
        val assetPath = "${AiModelConfig.QNN_STUBS_ROOT_DIR}/$fileName"
        val outFile = File(stubDir, fileName)

        if (outFile.exists()) {
            val assetSize = assetManager.open(assetPath).use { it.available() }
            if (outFile.length() == assetSize.toLong()) {
                continue
            }
        }

        assetManager.open(assetPath).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    return stubDir.absolutePath
}

fun copyAssetToInternalStorage(path: String, context: Context): String {
    val targetRoot = context.filesDir
    val outFile = File(targetRoot, path)

    if (!assetExists(context.assets, path = path)) {
        outFile.parentFile?.mkdirs()
        return outFile.absolutePath
    }

    if (outFile.exists()) {
        val assetSize = context.assets.open(path).use { it.available() }
        if (outFile.length() == assetSize.toLong()) {
            return "$targetRoot/$path"
        }
    }

    outFile.parentFile?.mkdirs()

    context.assets.open(path).use { input: InputStream ->
        FileOutputStream(outFile).use { output: OutputStream ->
            input.copyTo(output)
        }
    }

    return outFile.absolutePath
}

fun assetExists(assetManager: AssetManager, path: String): Boolean {
    val dir = path.substringBeforeLast('/', "")
    val fileName = path.substringAfterLast('/')

    val files = assetManager.list(dir) ?: return false
    return files.contains(fileName)
}