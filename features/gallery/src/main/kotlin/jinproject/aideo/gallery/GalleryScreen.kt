package jinproject.aideo.gallery

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jinproject.aideo.core.media.VideoItem
import jinproject.aideo.design.component.SubcomposeAsyncImageWithPreview
import jinproject.aideo.design.component.bar.RowScopedTitleAppBar
import jinproject.aideo.design.component.button.DefaultIconButton
import jinproject.aideo.design.component.effect.RememberEffect
import jinproject.aideo.design.component.layout.DownloadableLayout
import jinproject.aideo.design.component.layout.DownloadableUiState
import jinproject.aideo.design.component.text.DescriptionLargeText
import jinproject.aideo.design.utils.PreviewAideoTheme

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel = hiltViewModel(),
    localView: View = LocalView.current,
    navigateToSetting: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RememberEffect(Unit) {
        val windowInsetsController = WindowCompat.getInsetsController(
            (localView.context as ComponentActivity).window,
            localView
        )

        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }

    GalleryScreen(
        uiState = uiState,
        updateVideoList = viewModel::updateVideoList,
        navigateToSetting = navigateToSetting,
    )
}

@Composable
private fun GalleryScreen(
    uiState: DownloadableUiState,
    context: Context = LocalContext.current,
    updateVideoList: (List<String>) -> Unit,
    navigateToSetting: () -> Unit,
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
                    onClick = navigateToSetting,
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = jinproject.aideo.design.R.drawable.ic_build_filled),
                        contentDescription = "언어 설정",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
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
            navigateToSetting = {},
        )
    }
}