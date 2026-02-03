package jinproject.aideo.data

/**
 * "this.srt"
 */
fun String.toSubtitleFileIdentifier(): String = "$this.srt"
fun String.toThumbnailFileIdentifier(): String = "${this}_thumbnail.jpg"