package jinproject.aideo.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jinproject.aideo.design.R
import jinproject.aideo.design.component.bar.DefaultTitleAppBar
import jinproject.aideo.design.component.layout.DefaultLayout
import jinproject.aideo.design.component.paddingvalues.AideoPaddingValues
import jinproject.aideo.design.component.text.DescriptionLargeText
import jinproject.aideo.design.component.text.DescriptionSmallText
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.library.component.LibraryVideoGridList
import jinproject.aideo.library.component.SortDropdown

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LibraryScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun LibraryScreen(
    uiState: LibraryUiState,
    onEvent: (LibraryEvent) -> Unit,
) {
    DefaultLayout(
        topBar = {
            val backgroundColor = MaterialTheme.colorScheme.primary
            DefaultTitleAppBar(
                title = stringResource(R.string.library_title),
                backgroundColor = backgroundColor,
                contentColor = contentColorFor(backgroundColor),
            )
        },
        contentPaddingValues = AideoPaddingValues(
            horizontal = 16.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DescriptionSmallText(
                text = stringResource(
                    if (uiState.data.size == 1)
                        R.string.library_project_count
                    else
                        R.string.library_projects_count,
                    uiState.data.size
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            SortDropdown(
                selectedOption = uiState.sortOption,
                onOptionSelected = { onEvent(LibraryEvent.UpdateSortOption(it)) },
            )
        }

        when {
            uiState.data.isEmpty() -> {
                DescriptionLargeText(
                    text = stringResource(R.string.library_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .wrapContentSize()
                )
            }

            else -> {
                var videoItemSelection by remember {
                    mutableStateOf(VideoItemSelection())
                }

                LibraryVideoGridList(
                    uiState = uiState,
                    videoItemSelection = videoItemSelection,
                    removeVideos = {
                        onEvent(LibraryEvent.RemoveVideoUris(videoItemSelection.selectedUris))
                        videoItemSelection = VideoItemSelection()
                    }
                )
            }
        }
    }
}

@Preview
@Composable
private fun LibraryScreenPreview(
    @PreviewParameter(LibraryUiStatePreviewParameter::class)
    libraryUiState: LibraryUiState,
) {
    PreviewAideoTheme {
        LibraryScreen(
            uiState = libraryUiState,
            onEvent = {},
        )
    }
}
