package jinproject.aideo.gallery.gallery.component

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.google.android.play.core.aipacks.model.AiPackStatus
import jinproject.aideo.core.SnackBarMessage
import jinproject.aideo.core.inference.AiModelConfig
import jinproject.aideo.core.utils.LocalShowSnackBar
import jinproject.aideo.core.utils.getAiPackManager
import jinproject.aideo.design.R
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.gallery.TranscribeService
import jinproject.aideo.gallery.gallery.GalleryUiState
import jinproject.aideo.gallery.gallery.GalleryUiStatePreviewParameter
import jinproject.aideo.gallery.gallery.model.GalleryVideoItem
import jinproject.aideo.gallery.gallery.model.toVideoItem
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun VideoListContent(
    videoItems: ImmutableList<GalleryVideoItem>,
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current,
) {
    val localShowSnackBar = LocalShowSnackBar.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(
            items = videoItems,
            key = { video -> video.id }
        ) { video ->
            VideoCard(
                videoItem = video,
                modifier = Modifier.animateItem(),
                onClick = {
                    context.getAiPackManager()
                        .getPackStates(listOf(AiModelConfig.SPEECH_BASE_PACK))
                        .addOnCompleteListener { task ->
                            when (task.result.packStates()[AiModelConfig.SPEECH_BASE_PACK]?.status()) {
                                AiPackStatus.COMPLETED -> {
                                    context.startForegroundService(
                                        Intent(
                                            context, TranscribeService::class.java
                                        ).apply {
                                            putExtra("videoItem", video.toVideoItem())
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
                }
            )
        }
    }
}

@Preview
@Composable
private fun VideoListContentPreview(
    @PreviewParameter(GalleryUiStatePreviewParameter::class)
    galleryUiState: GalleryUiState,
) {
    PreviewAideoTheme {
        VideoListContent(
            videoItems = galleryUiState.data,
        )
    }
}