package jinproject.aideo.gallery.gallery

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jinproject.aideo.core.BillingModule.Product
import jinproject.aideo.core.utils.LocalBillingModule
import jinproject.aideo.design.R
import jinproject.aideo.design.component.bar.RowScopedTitleAppBar
import jinproject.aideo.design.component.button.DefaultIconButton
import jinproject.aideo.design.component.layout.DownloadableLayout
import jinproject.aideo.design.component.layout.DownloadableUiState
import jinproject.aideo.design.component.text.DescriptionLargeText
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.gallery.gallery.component.VideoGridContent

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel = hiltViewModel(),
    navigateToSetting: () -> Unit,
    navigateToSubscription: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val billingModule = LocalBillingModule.current

    val isRemovedAdPurchased by produceState<Boolean>(
        true,
        billingModule,
        billingModule.isReady,
    ) {
        value = billingModule.isReady && billingModule.isProductPurchased(Product.REMOVE_AD)
    }

    GalleryScreen(
        uiState = uiState,
        isRemovedAdPurchased = isRemovedAdPurchased,
        onEvent = viewModel::onEvent,
        navigateToSetting = navigateToSetting,
        navigateToSubscription = navigateToSubscription
    )
}

@Composable
private fun GalleryScreen(
    uiState: DownloadableUiState,
    isRemovedAdPurchased: Boolean,
    context: Context = LocalContext.current,
    onEvent: (GalleryEvent) -> Unit,
    navigateToSetting: () -> Unit,
    navigateToSubscription: () -> Unit,
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
            RowScopedTitleAppBar(
                title = stringResource(R.string.gallery_title),
                backgroundColor = backgroundColor,
                contentColor = contentColorFor(backgroundColor),
            ) {
                DefaultIconButton(
                    icon = R.drawable.ic_add_image_outlined,
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(
                                PickVisualMedia.VideoOnly
                            )
                        )
                    },
                    backgroundTint = backgroundColor,
                    iconTint = contentColorFor(backgroundColor),
                )
                DefaultIconButton(
                    icon = R.drawable.ic_settings_outlined,
                    onClick = navigateToSetting,
                    backgroundTint = backgroundColor,
                    iconTint = contentColorFor(backgroundColor),
                )
                AnimatedVisibility(!isRemovedAdPurchased) {
                    DefaultIconButton(
                        icon = R.drawable.ic_shopping,
                        onClick = navigateToSubscription,
                        backgroundTint = backgroundColor,
                        iconTint = contentColorFor(backgroundColor),
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
                    text = stringResource(R.string.gallery_empty),
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize()
                )
            }

            else -> {
                var videoItemSelection by remember {
                    mutableStateOf(VideoItemSelection.getDefault())
                }

                VideoGridContent(
                    videoItems = galleryUiState.data,
                    videoItemSelection = videoItemSelection,
                    context = context,
                    onRemoveVideos = { uris ->
                        onEvent(GalleryEvent.RemoveVideoSet(uris))
                        videoItemSelection = VideoItemSelection.getDefault()
                    }
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
            isRemovedAdPurchased = true,
            onEvent = {},
            navigateToSetting = {},
            navigateToSubscription = {},
        )
    }
}