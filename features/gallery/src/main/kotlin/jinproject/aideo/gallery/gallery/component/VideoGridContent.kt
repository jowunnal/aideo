package jinproject.aideo.gallery.gallery.component

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.google.android.play.core.aipacks.model.AiPackStatus
import jinproject.aideo.core.SnackBarMessage
import jinproject.aideo.core.inference.AiModelConfig
import jinproject.aideo.core.media.VideoItem
import jinproject.aideo.core.utils.LocalShowSnackBar
import jinproject.aideo.core.utils.getAiPackManager
import jinproject.aideo.design.R
import jinproject.aideo.design.component.button.DefaultIconButton
import jinproject.aideo.design.theme.AideoColor
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.gallery.TranscribeService
import jinproject.aideo.gallery.gallery.GalleryUiState
import jinproject.aideo.gallery.gallery.GalleryUiStatePreviewParameter
import jinproject.aideo.gallery.gallery.VideoItemSelection
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun VideoGridContent(
    videoItems: ImmutableList<VideoItem>,
    videoItemSelection: VideoItemSelection,
    context: Context = LocalContext.current,
    onRemoveVideos: (Set<String>) -> Unit,
) {
    val localShowSnackBar = LocalShowSnackBar.current

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        ) {
            items(videoItems) { video ->
                VideoGridItem(
                    videoItem = video,
                    videoItemSelection = videoItemSelection,
                    generateSubtitle = {
                        context.getAiPackManager()
                            .getPackStates(listOf(AiModelConfig.SPEECH_BASE_PACK))
                            .addOnCompleteListener { task ->
                                when (task.result.packStates()[AiModelConfig.SPEECH_BASE_PACK]?.status()) {
                                    AiPackStatus.COMPLETED -> {
                                        context.startForegroundService(
                                            Intent(
                                                context, TranscribeService::class.java
                                            ).apply {
                                                putExtra("videoItem", video)
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
                    enterSelectionMode = {
                        videoItemSelection.isSelectionMode = true
                    },
                    addSelectedUri = { uri ->
                        videoItemSelection.addSelectedUri(uri)
                    },
                    removeSelectedUri = { uri ->
                        videoItemSelection.removeSelectedUri(uri)
                    }
                )
            }
        }

        DeleteIconOnSelectionMode(
            selectionMode = videoItemSelection.isSelectionMode,
            onClick = {
                onRemoveVideos(videoItemSelection.selectedUris)
            }
        )
    }
}

@Composable
private fun BoxScope.DeleteIconOnSelectionMode(
    selectionMode: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = selectionMode,
        modifier = Modifier
            .align(BottomCenter)
            .padding(bottom = 20.dp),
    ) {
        DefaultIconButton(
            icon = R.drawable.ic_delete,
            onClick = onClick,
            modifier = Modifier
                .shadow(
                    1.dp,
                    RoundedCornerShape(100.dp)
                )
                .background(
                    MaterialTheme.colorScheme.background,
                    RoundedCornerShape(100.dp)
                )
                .padding(4.dp),
            iconTint = AideoColor.red.color,
            iconSize = 32.dp
        )
    }
}

@Preview
@Composable
private fun VideoGridContentUnSelectionPreview(
    @PreviewParameter(GalleryUiStatePreviewParameter::class)
    galleryUiState: GalleryUiState,
) {
    PreviewAideoTheme {
        VideoGridContent(
            videoItemSelection = VideoItemSelection.getDefault(),
            videoItems = galleryUiState.data,
            onRemoveVideos = {},
        )
    }
}

@Preview
@Composable
private fun VideoGridContentSelectionPreview(
    @PreviewParameter(GalleryUiStatePreviewParameter::class)
    galleryUiState: GalleryUiState,
) {
    PreviewAideoTheme {
        VideoGridContent(
            videoItemSelection = VideoItemSelection.getDefault().apply {
                isSelectionMode = true
            },
            videoItems = galleryUiState.data,
            onRemoveVideos = {},
        )
    }
}