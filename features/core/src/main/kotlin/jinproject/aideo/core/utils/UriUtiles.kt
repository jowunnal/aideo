package jinproject.aideo.core.utils

import androidx.core.net.toUri

fun String.toVideoItemId(): Long = toUri().lastPathSegment?.toLong()
?: throw IllegalArgumentException("Invalid video ID")