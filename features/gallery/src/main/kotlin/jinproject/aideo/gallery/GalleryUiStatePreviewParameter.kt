package jinproject.aideo.gallery

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import jinproject.aideo.core.media.VideoItem
import kotlinx.collections.immutable.persistentListOf

class GalleryUiStatePreviewParameter : PreviewParameterProvider<GalleryUiState> {
    override val values: Sequence<GalleryUiState>
        get() = sequenceOf(
            GalleryUiState(
                data = persistentListOf(
                    VideoItem(
                        uri = "",
                        id = 0,
                        title = "title",
                        thumbnailPath = "",
                    ),
                    VideoItem(
                        uri = "",
                        id = 0,
                        title = "title",
                        thumbnailPath = "",
                    ),
                    VideoItem(
                        uri = "",
                        id = 0,
                        title = "title",
                        thumbnailPath = "",
                    ),
                ),
                languageCode = "",
            )
        )
}