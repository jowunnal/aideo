package jinproject.aideo.gallery.gallery

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import jinproject.aideo.gallery.gallery.model.GalleryVideoItem
import jinproject.aideo.gallery.gallery.model.VideoStatus
import kotlinx.collections.immutable.persistentListOf

class GalleryUiStatePreviewParameter : PreviewParameterProvider<GalleryUiState> {
    override val values: Sequence<GalleryUiState>
        get() = sequenceOf(
            GalleryUiState(
                data = persistentListOf(
                    GalleryVideoItem(
                        uri = "",
                        id = 0,
                        thumbnailPath = "",
                        date = "2025.01.28",
                        status = VideoStatus.COMPLETED
                    ),
                    GalleryVideoItem(
                        uri = "",
                        id = 1,
                        thumbnailPath = "",
                        date = "2025.01.27",
                        status = VideoStatus.NEED_TRANSLATE,
                    ),
                    GalleryVideoItem(
                        uri = "",
                        id = 2,
                        thumbnailPath = "",
                        date = "2025.01.26",
                        status = VideoStatus.NEED_INFERENCE,
                    ),
                ),
                languageCode = "",
            )
        )
}