package jinproject.aideo.gallery

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.inspector.MetadataRetriever
import jinproject.aideo.core.media.VideoItem
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.design.component.PopUp
import jinproject.aideo.design.component.PopUpInfo
import jinproject.aideo.design.component.SubcomposeAsyncImageWithPreview
import jinproject.aideo.design.component.bar.RowScopedTitleAppBar
import jinproject.aideo.design.component.button.DefaultIconButton
import jinproject.aideo.design.component.button.clickableAvoidingDuplication
import jinproject.aideo.design.component.effect.RememberEffect
import jinproject.aideo.design.component.layout.DownloadableLayout
import jinproject.aideo.design.component.layout.DownloadableUiState
import jinproject.aideo.design.component.text.DescriptionLargeText
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.design.utils.tu

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GalleryScreen(
        uiState = uiState,
        updateVideoList = viewModel::updateVideoList,
        updateLanguageCode = viewModel::updateLanguage
    )
}

@Composable
private fun GalleryScreen(
    uiState: DownloadableUiState,
    context: Context = LocalContext.current,
    density: Density = LocalDensity.current,
    updateVideoList: (List<String>) -> Unit,
    updateLanguageCode: (LanguageCode) -> Unit,
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty())
            updateVideoList(
                uris.map {
                    it.toString()
                }
            )
    }

    var popUpInfo by remember { mutableStateOf(PopUpInfo(IntOffset(0, 0))) }
    val iconHeight = with(density) {
        24.dp.roundToPx()
    }
    val popUpHalfWidth = with(density) {
        112.tu.roundToPx() / 2
    }

    PopUp(popUpInfo = popUpInfo) {
        Column(
            modifier = Modifier
                .shadow(
                    1.dp,
                    RoundedCornerShape(20.dp)
                )
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(20.dp))
        ) {
            LanguageCode.entries.toTypedArray().forEach { language ->
                DescriptionMediumText(
                    text = language.name,
                    modifier = Modifier
                        .clickableAvoidingDuplication {
                            updateLanguageCode(language)
                        }
                        .padding(vertical = 8.dp, horizontal = 16.dp)
                        .graphicsLayer {
                            alpha = if (language.code == (uiState as GalleryUiState).languageCode) 1f else 0.5f
                        },
                )
            }
        }
    }

    RememberEffect(Unit) {
        (context as Activity).requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    DownloadableLayout(
        topBar = {
            RowScopedTitleAppBar(
                title = "갤러리"
            ) {
                DefaultIconButton(
                    icon = jinproject.aideo.design.R.drawable.icon_plus,
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(
                                PickVisualMedia.VideoOnly
                            )
                        )
                    },
                )
                IconButton(
                    onClick = {
                        popUpInfo.changeVisibility(!popUpInfo.visibility)
                    },
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = jinproject.aideo.design.R.drawable.ic_build_filled),
                        contentDescription = "언어 설정",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.onGloballyPositioned { layoutCoordinates ->
                            popUpInfo = PopUpInfo(
                                offset = run {
                                    val position = layoutCoordinates.positionInWindow()

                                    IntOffset(
                                        position.x.toInt() - popUpHalfWidth,
                                        iconHeight
                                    )
                                }
                            )
                        }
                    )
                }
            }
        },
        downloadableUiState = uiState,
    ) { state ->
        val galleryUiState = state as GalleryUiState

        when {
            galleryUiState.data.isEmpty() -> {
                DescriptionLargeText(
                    text = "비어 있음",
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize()
                )
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    items(galleryUiState.data) { video ->
                        VideoGridItem(
                            videoItem = video,
                            onClick = {
                                context.startForegroundService(
                                    Intent(
                                        context, TranscribeService::class.java
                                    ).apply {
                                        putExtra("videoItem", video)
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoGridItem(
    videoItem: VideoItem,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Column {
            SubcomposeAsyncImageWithPreview(
                placeHolderPreview = jinproject.aideo.design.R.drawable.test,
                model = videoItem.thumbnailPath,
                contentDescription = videoItem.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentScale = ContentScale.Crop
            )
            Text(
                text = videoItem.title,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
                maxLines = 2
            )
        }
    }
}

@Preview
@Composable
private fun GalleryScreenPreview(
    @PreviewParameter(GalleryUiStatePreviewParameter::class)
    galleryUiState: GalleryUiState,
) {
    PreviewAideoTheme {
        GalleryScreen(
            uiState = galleryUiState,
            updateVideoList = {},
            updateLanguageCode = {},
        )
    }
}