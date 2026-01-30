package jinproject.aideo.gallery.gallery

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.play.core.aipacks.model.AiPackStatus
import jinproject.aideo.core.BillingModule.Product
import jinproject.aideo.core.SnackBarMessage
import jinproject.aideo.core.inference.AiModelConfig
import jinproject.aideo.core.media.VideoItem
import jinproject.aideo.core.utils.LocalBillingModule
import jinproject.aideo.core.utils.LocalShowSnackBar
import jinproject.aideo.core.utils.getAiPackManager
import jinproject.aideo.design.R
import jinproject.aideo.design.component.SubcomposeAsyncImageWithPreview
import jinproject.aideo.design.component.bar.RowScopedTitleAppBar
import jinproject.aideo.design.component.button.DefaultIconButton
import jinproject.aideo.design.component.button.clickableAvoidingDuplication
import jinproject.aideo.design.component.layout.DownloadableLayout
import jinproject.aideo.design.component.layout.DownloadableUiState
import jinproject.aideo.design.component.text.DescriptionLargeText
import jinproject.aideo.design.component.text.DescriptionSmallText
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.gallery.TranscribeService
import jinproject.aideo.gallery.subs.SubscriptionUiState

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
        updateVideoList = viewModel::updateVideoList,
        navigateToSetting = navigateToSetting,
        navigateToSubscription = navigateToSubscription
    )
}

@Composable
private fun GalleryScreen(
    uiState: DownloadableUiState,
    isRemovedAdPurchased: Boolean,
    context: Context = LocalContext.current,
    updateVideoList: (List<String>) -> Unit,
    navigateToSetting: () -> Unit,
    navigateToSubscription: () -> Unit,
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
    val localShowSnackBar = LocalShowSnackBar.current

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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(20.dp))
            .clickableAvoidingDuplication(onClick = onClick),
    ) {
        Box(Modifier.weight(1f)) {
            SubcomposeAsyncImageWithPreview(
                placeHolderPreview = R.drawable.test,
                model = videoItem.thumbnailPath,
                contentDescription = videoItem.title,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                contentScale = ContentScale.FillHeight,
            )
            Image(
                imageVector = ImageVector.vectorResource(R.drawable.ic_playback_play),
                contentDescription = stringResource(R.string.content_desc_play_video),
                modifier = Modifier
                    .shadow(1.dp, RoundedCornerShape(20.dp))
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .padding(4.dp)
                    .align(Alignment.Center),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
            )
        }
        DescriptionSmallText(
            text = videoItem.title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
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
            updateVideoList = {},
            navigateToSetting = {},
            navigateToSubscription = {},
        )
    }
}