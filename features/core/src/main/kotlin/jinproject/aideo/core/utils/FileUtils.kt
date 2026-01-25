package jinproject.aideo.core.utils

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.collections.contains

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