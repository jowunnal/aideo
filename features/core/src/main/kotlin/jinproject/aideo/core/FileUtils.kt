package jinproject.aideo.core

fun String.toAudioFileIdentifier(): String = "${this}_audio.wav"
fun String.toSubtitleFileIdentifier(): String = "$this.srt"
fun String.toThumbnailFileIdentifier(): String = "${this}_thumbnail.jpg"