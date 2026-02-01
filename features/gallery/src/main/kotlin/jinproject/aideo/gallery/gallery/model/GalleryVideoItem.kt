package jinproject.aideo.gallery.gallery.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import jinproject.aideo.core.media.VideoItem
import kotlinx.parcelize.Parcelize

@Immutable
@Parcelize
data class GalleryVideoItem(
    val uri: String,
    val id: Long,
    val thumbnailPath: String?,
    val date: String,
) : Parcelable

fun GalleryVideoItem.toVideoItem(): VideoItem = VideoItem(
    uri = uri,
    id = id,
    thumbnailPath = thumbnailPath,
    date = date,
    title = "",
)