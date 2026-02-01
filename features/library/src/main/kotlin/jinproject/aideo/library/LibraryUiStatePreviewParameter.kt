package jinproject.aideo.library

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import jinproject.aideo.library.model.LibraryVideoItem
import jinproject.aideo.library.model.SortOption
import kotlinx.collections.immutable.persistentListOf

class LibraryUiStatePreviewParameter : PreviewParameterProvider<LibraryUiState> {

    override val values: Sequence<LibraryUiState>
        get() = sequenceOf(
            LibraryUiState(
                data = persistentListOf(
                    LibraryVideoItem(
                        uri = "test1",
                        id = 1,
                        thumbnailPath = null,
                        date = "2025.01.28"
                    ),
                    LibraryVideoItem(
                        uri = "test2",
                        id = 2,
                        thumbnailPath = null,
                        date = "2025.01.27"
                    ),
                    LibraryVideoItem(
                        uri = "test3",
                        id = 3,
                        thumbnailPath = null,
                        date = "2025.01.26"
                    ),
                    LibraryVideoItem(
                        uri = "test4",
                        id = 4,
                        thumbnailPath = null,
                        date = "2025.01.25"
                    ),
                ),
                sortOption = SortOption.NEWEST
            )
        )
}
