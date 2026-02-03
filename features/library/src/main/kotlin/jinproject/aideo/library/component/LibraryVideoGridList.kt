package jinproject.aideo.library.component

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.google.android.play.core.aipacks.model.AiPackStatus
import jinproject.aideo.core.SnackBarMessage
import jinproject.aideo.core.inference.AiModelConfig
import jinproject.aideo.core.utils.LocalShowSnackBar
import jinproject.aideo.core.utils.getAiPackManager
import jinproject.aideo.design.R
import jinproject.aideo.design.component.button.clickableAvoidingDuplication
import jinproject.aideo.design.theme.AideoColor
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.library.LibraryUiState
import jinproject.aideo.library.LibraryUiStatePreviewParameter
import jinproject.aideo.library.VideoItemSelection
import jinproject.aideo.library.model.toVideoItem

@Composable
internal fun LibraryVideoGridList(
    uiState: LibraryUiState,
    videoItemSelection: VideoItemSelection,
    context: Context = LocalContext.current,
    removeVideos: (Set<String>) -> Unit,
) {
    val localShowSnackBar = LocalShowSnackBar.current

    Box {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize(),
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
                    videoItemSelection = videoItemSelection,
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
                                                putExtra(
                                                    "videoItem",
                                                    videoItem.toVideoItem()
                                                )
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
                    modifier = Modifier.animateItem()
                )
            }
        }

        AnimatedVisibility(
            visible = videoItemSelection.isSelectionMode,
            modifier = Modifier
                .align(BottomCenter)
                .padding(bottom = 30.dp),
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_delete),
                contentDescription = "Delete video",
                modifier = Modifier
                    .size(48.dp)
                    .shadow(
                        1.dp,
                        RoundedCornerShape(100.dp)
                    )
                    .background(
                        MaterialTheme.colorScheme.background,
                        RoundedCornerShape(100.dp)
                    )
                    .clickableAvoidingDuplication {
                        removeVideos(videoItemSelection.selectedUris)
                    }
                    .padding(8.dp),
                tint = AideoColor.red.color
            )
        }
    }
}

@Preview
@Composable
private fun VideoGridContentUnSelectionPreview(
    @PreviewParameter(LibraryUiStatePreviewParameter::class)
    libraryUiState: LibraryUiState,
) {
    PreviewAideoTheme {
        LibraryVideoGridList(
            uiState = libraryUiState,
            videoItemSelection = VideoItemSelection().apply {
                updateIsSelectionMode(false)
            },
            removeVideos = {}
        )
    }
}

@Preview
@Composable
private fun VideoGridContentSelectionPreview(
    @PreviewParameter(LibraryUiStatePreviewParameter::class)
    libraryUiState: LibraryUiState,
) {
    PreviewAideoTheme {
        LibraryVideoGridList(
            uiState = libraryUiState,
            videoItemSelection = VideoItemSelection().apply {
                updateIsSelectionMode(true)
                addSelectedUri(libraryUiState.data[0].uri)
                addSelectedUri(libraryUiState.data[1].uri)
            },
            removeVideos = {}
        )
    }
}