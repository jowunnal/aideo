package jinproject.aideo.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jinproject.aideo.design.component.SubcomposeAsyncImageWithPreview
import jinproject.aideo.design.component.bar.OneButtonTitleAppBar
import jinproject.aideo.design.component.layout.DefaultLayout
import jinproject.aideo.design.component.layout.DownloadableLayout
import jinproject.aideo.design.component.layout.DownloadableUiState
import jinproject.aideo.design.component.text.DescriptionLargeText
import jinproject.aideo.design.utils.PreviewAideoTheme

@Composable
fun GalleryScreen(
    navigateToPlayer: (String) -> Unit,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GalleryScreen(
        uiState = uiState,
        addVideo = viewModel::updateVideoList,
        navigateToPlayer = navigateToPlayer,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryScreen(
    uiState: DownloadableUiState,
    addVideo: (List<String>) -> Unit,
    navigateToPlayer: (String) -> Unit,
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(Integer.MAX_VALUE)
    ) { uris ->
        if (uris.isNotEmpty())
            addVideo(
                uris.mapNotNull { uri ->
                    uri.lastPathSegment
                }
            )
    }

    DownloadableLayout(
        topBar = {
            OneButtonTitleAppBar(
                buttonAlignment = Alignment.CenterEnd,
                title = "갤러리",
                onBackClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.VideoOnly
                        )
                    )
                },
                icon = jinproject.aideo.design.R.drawable.icon_plus
            )
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
                            video = video,
                            onClick = { navigateToPlayer(video.uri) }
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun VideoGridItem(
    video: VideoItem,
    onClick: () -> Unit
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
                model = video.thumbnailPath,
                contentDescription = video.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentScale = ContentScale.Crop
            )
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
                maxLines = 2
            )
        }
    }
}

@Preview
@Composable
private fun GalleryScreenPreview() {
    PreviewAideoTheme {
        GalleryScreen(
            navigateToPlayer = {}
        )
    }
}