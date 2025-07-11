package jinproject.aideo.data

fun String.toAudioFileAACIdentifier(): String = "${this}_audio.aac"
fun toAudioFileWAVIdentifier(id: Long): String = "${id}_audio.wav"

/**
 * "this.srt"
 */
fun String.toSubtitleFileIdentifier(): String = "$this.srt"
fun String.toThumbnailFileIdentifier(): String = "${this}_thumbnail.jpg"