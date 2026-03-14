package jinproject.aideo.data

import android.net.Uri
import androidx.core.net.toUri

object SubtitleFileConfig {
    const val SUBTITLE_FORMAT = "srt"
    const val SUBTITLE_EXTENSION = ".$SUBTITLE_FORMAT"
    fun toSubtitleFileId(contentUri: String): Long? =
        contentUri.toUri().lastPathSegment?.toLongOrNull()

    fun toSubtitleFileIdentifier(fileName: String): FileIdentifier =
        FileIdentifier("${fileName}$SUBTITLE_EXTENSION")

    fun toSubtitleFileIdentifier(id: Long, languageCode: String): FileIdentifier =
        toSubtitleFileIdentifier("${id}_$languageCode")

    fun toThumbnailFileIdentifier(fileId: Long): FileIdentifier =
        FileIdentifier("${fileId}_thumbnail.jpg")
}

@JvmInline
value class FileIdentifier(val identifier: String)