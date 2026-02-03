package jinproject.aideo.gallery.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.common.math.LinearTransformation.horizontal
import jinproject.aideo.design.R
import jinproject.aideo.design.component.VerticalSpacer
import jinproject.aideo.design.component.bar.DefaultTitleAppBar
import jinproject.aideo.design.component.layout.DownloadableLayout
import jinproject.aideo.design.component.layout.DownloadableUiState
import jinproject.aideo.design.component.paddingvalues.AideoPaddingValues
import jinproject.aideo.design.component.text.DescriptionLargeText
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.gallery.gallery.component.NewProjectCard
import jinproject.aideo.gallery.gallery.component.RecentlyCompletedHeader
import jinproject.aideo.gallery.gallery.component.VideoListContent
import jinproject.aideo.gallery.gallery.model.GalleryVideoItem

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel = hiltViewModel(),
    navigateToLibrary: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleStartEffect(Unit) {
        viewModel.onEvent(GalleryEvent.RestartUiState)

        onStopOrDispose {}
    }

    GalleryScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        navigateToLibrary = navigateToLibrary,
    )
}

@Composable
private fun GalleryScreen(
    uiState: DownloadableUiState,
    onEvent: (GalleryEvent) -> Unit,
    navigateToLibrary: () -> Unit,
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty())
            onEvent(
                GalleryEvent.UpdateVideoSet(
                    uris.map {
                        it.toString()
                    }.toSet()
                )
            )
    }

    DownloadableLayout(
        topBar = {
            val backgroundColor = MaterialTheme.colorScheme.primary
            DefaultTitleAppBar(
                title = stringResource(R.string.gallery_title),
                backgroundColor = backgroundColor,
                contentColor = contentColorFor(backgroundColor),
            )
        },
        downloadableUiState = uiState,
        contentPaddingValues = AideoPaddingValues(
            horizontal = 16.dp
        )
    ) { state ->
        val galleryUiState = state as GalleryUiState

        NewProjectCard(
            modifier = Modifier.padding(vertical = 20.dp),
            onClick = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(
                        PickVisualMedia.VideoOnly
                    )
                )
            }
        )

        when {
            galleryUiState.data.isEmpty() -> {
                DescriptionLargeText(
                    text = stringResource(R.string.gallery_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .wrapContentSize()
                )
            }

            else -> {
                RecentlyCompletedHeader(
                    modifier = Modifier,
                    onViewAllClick = navigateToLibrary
                )

                VideoListContent(
                    videoItems = galleryUiState.data,
                    modifier = Modifier.weight(1f),
                )
            }
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
            onEvent = {},
            navigateToLibrary = {},
        )
    }
}