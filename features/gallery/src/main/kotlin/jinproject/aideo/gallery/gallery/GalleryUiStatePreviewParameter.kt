package jinproject.aideo.gallery.gallery

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import jinproject.aideo.gallery.gallery.model.GalleryVideoItem
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
                    ),
                    GalleryVideoItem(
                        uri = "",
                        id = 1,
                        thumbnailPath = "",
                        date = "2025.01.27",
                    ),
                    GalleryVideoItem(
                        uri = "",
                        id = 2,
                        thumbnailPath = "",
                        date = "2025.01.26",
                    ),
                ),
                languageCode = "",
            )
        )
}