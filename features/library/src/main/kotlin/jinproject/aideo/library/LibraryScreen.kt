package jinproject.aideo.library

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.play.core.aipacks.model.AiPackStatus
import jinproject.aideo.core.SnackBarMessage
import jinproject.aideo.core.inference.AiModelConfig
import jinproject.aideo.core.media.VideoItem
import jinproject.aideo.core.utils.LocalShowSnackBar
import jinproject.aideo.core.utils.getAiPackManager
import jinproject.aideo.design.R
import jinproject.aideo.design.component.bar.DefaultTitleAppBar
import jinproject.aideo.design.component.layout.DefaultLayout
import jinproject.aideo.design.component.paddingvalues.AideoPaddingValues
import jinproject.aideo.design.component.text.DescriptionLargeText
import jinproject.aideo.design.component.text.DescriptionSmallText
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.library.component.LibraryVideoCard
import jinproject.aideo.library.component.SortDropdown
import jinproject.aideo.library.model.toVideoItem

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
    context: Context = LocalContext.current,
    onEvent: (LibraryEvent) -> Unit,
) {
    val localShowSnackBar = LocalShowSnackBar.current

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
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = uiState.data,
                        key = { it.id }
                    ) { videoItem ->
                        LibraryVideoCard(
                            videoItem = videoItem,
                            onClick = {
                                context.getAiPackManager()
                                    .getPackStates(listOf(AiModelConfig.SPEECH_BASE_PACK))
                                    .addOnCompleteListener { task ->
                                        when (task.result.packStates()[AiModelConfig.SPEECH_BASE_PACK]?.status()) {
                                            AiPackStatus.COMPLETED -> {
                                                context.startForegroundService(
                                                    Intent(
                                                        context,
                                                        Class.forName("jinproject.aideo.gallery.TranscribeService")
                                                    ).apply {
                                                        putExtra("videoItem", videoItem.toVideoItem())
                                                    }
                                                )
                                            }

                                            AiPackStatus.CANCELED, AiPackStatus.FAILED, AiPackStatus.PENDING, AiPackStatus.NOT_INSTALLED -> {
                                                context.getAiPackManager()
                                                    .fetch(listOf(AiModelConfig.SPEECH_BASE_PACK))
                                                localShowSnackBar.invoke(
                                                    SnackBarMessage(
                                                        headerMessage = context.getString(R.string.download_failed_or_pending),
                                                        contentMessage = context.getString(R.string.download_retry_request)
                                                    )
                                                )
                                            }

                                            else -> {}
                                        }
                                    }
                            },
                        )
                    }
                }
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
