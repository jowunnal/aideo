package jinproject.aideo.data

import android.content.Context
import java.io.File

object FileConstants {
    const val SUBTITLE_DIR = "subtitles"
    const val AUDIO_DIR = "audio"
    const val SUBTITLE_EXTENSION = ".srt"
    const val AUDIO_EXTENSION = ".aac"
}

data class SrtEntry(
    val index: Int,
    val startTime: String,
    val endTime: String,
    val text: String
)

data class SubtitleFileInfo(
    val file: File,
    val languageCode: String
)

class SubtitleFileManager(private val context: Context) {
    private val dir = File(context.filesDir, FileConstants.SUBTITLE_DIR).apply { mkdirs() }

    fun getSubtitleFileName(baseName: String, languageCode: String): String =
        "${baseName}_$languageCode${FileConstants.SUBTITLE_EXTENSION}"

    fun saveSubtitleFile(baseName: String, languageCode: String, srtContent: String): SubtitleFileInfo {
        val file = File(dir, getSubtitleFileName(baseName, languageCode)).apply {
            writeText(srtContent)
        }
        return SubtitleFileInfo(file, languageCode)
    }

    fun getSubtitleFile(baseName: String, languageCode: String): SubtitleFileInfo? {
        val file = File(dir, getSubtitleFileName(baseName, languageCode))
        return if (file.exists()) SubtitleFileInfo(file, languageCode) else null
    }

    fun deleteSubtitleFile(baseName: String, languageCode: String): Boolean {
        val file = File(dir, getSubtitleFileName(baseName, languageCode))
        return file.delete()
    }

    fun listSubtitleFiles(baseName: String): List<SubtitleFileInfo> =
        dir.listFiles { f -> f.name.startsWith(baseName) && f.name.endsWith(FileConstants.SUBTITLE_EXTENSION) }
            ?.map { file ->
                val lang = file.name.removePrefix("${baseName}_").removeSuffix(FileConstants.SUBTITLE_EXTENSION)
                SubtitleFileInfo(file, lang)
            } ?: emptyList()

    fun generateSrt(entries: List<SrtEntry>): String =
        entries.joinToString("\n\n") { entry ->
            "${entry.index}\n${entry.startTime} --> ${entry.endTime}\n${entry.text}"
        }
}

data class AudioFileInfo(
    val file: File
)

class AudioFileManager(private val context: Context) {
    private val dir = File(context.filesDir, FileConstants.AUDIO_DIR).apply { mkdirs() }

    fun getAudioFileName(baseName: String): String =
        "$baseName${FileConstants.AUDIO_EXTENSION}"

    fun saveAudioFile(baseName: String, bytes: ByteArray): AudioFileInfo {
        val file = File(dir, getAudioFileName(baseName)).apply {
            writeBytes(bytes)
        }
        return AudioFileInfo(file)
    }

    fun getAudioFile(baseName: String): AudioFileInfo? {
        val file = File(dir, getAudioFileName(baseName))
        return if (file.exists()) AudioFileInfo(file) else null
    }

    fun deleteAudioFile(baseName: String): Boolean {
        val file = File(dir, getAudioFileName(baseName))
        return file.delete()
    }
}