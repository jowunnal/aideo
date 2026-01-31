package jinproject.aideo.gallery.gallery.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import jinproject.aideo.core.media.VideoItem
import jinproject.aideo.design.R
import jinproject.aideo.design.component.SubcomposeAsyncImageWithPreview
import jinproject.aideo.design.component.button.combinedClickableAvoidingDuplication
import jinproject.aideo.design.component.text.DescriptionSmallText
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.design.utils.fillBounds
import jinproject.aideo.gallery.gallery.GalleryUiState
import jinproject.aideo.gallery.gallery.GalleryUiStatePreviewParameter
import jinproject.aideo.gallery.gallery.VideoItemSelection

@Composable
internal fun VideoGridItem(
    videoItem: VideoItem,
    videoItemSelection: VideoItemSelection,
    generateSubtitle: () -> Unit,
    enterSelectionMode: () -> Unit,
    addSelectedUri: (String) -> Unit,
    removeSelectedUri: (String) -> Unit,
) {
    val isSelected by remember {
        derivedStateOf {
            videoItemSelection.selectedUris.contains(videoItem.uri)
        }
    }
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val animateBySelectionModel by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(250),
    )
    val animateColorBySelected by animateColorAsState(
        targetValue = if (isSelected) Color.White else surfaceVariantColor,
        animationSpec = tween(250),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .shadow(
                4.dp,
                RoundedCornerShape(20.dp)
            )
            .background(
                MaterialTheme.colorScheme.background,
                RoundedCornerShape(20.dp)
            )
            .drawWithCache {
                val iconSize = 18.dp.roundToPx()
                val padding = 8.dp.roundToPx()
                val xPos = iconSize.toFloat()
                val yPos = padding + 8.dp.toPx()
                val centerOffset = Offset(
                    x = xPos + iconSize / 2f,
                    y = yPos + iconSize / 2f - 8f
                )
                val radius = iconSize / 2f
                val outerCircleRadius = radius + 4.dp.toPx()
                val innerCircleRadius = radius + 2.dp.toPx()
                val outerCircleStroke = Stroke(1.dp.toPx())

                val path =
                    PathParser()
                        .parsePathString("M192 416c8.188 0 16.38-3.125 22.62-9.375l256-256C476.9 144.4 480 136.2 480 128c0-18.28-14.95-32-32-32c-8.188 0-16.38 3.125-22.62 9.375L192 338.8 60.62 206.4C54.38 200.2 46.19 196 38 196c-17.05 0-32 14.95-32 32c0 8.188 3.125 16.38 9.375 22.62l128 128C175.6 412.9 183.8 416 192 416z")
                        .toPath()
                        .apply {
                            fillBounds(
                                strokeWidthPx = 1f,
                                maxWidth = iconSize,
                                maxHeight = iconSize,
                            )
                            translate(Offset(x = xPos, y = yPos))
                        }

                onDrawWithContent {
                    drawContent()

                    if (videoItemSelection.isSelectionMode) {
                        drawCircle(
                            color = Color.Black,
                            radius = outerCircleRadius,
                            center = centerOffset,
                            style = outerCircleStroke
                        )
                        drawCircle(
                            color = animateColorBySelected,
                            radius = innerCircleRadius,
                            center = centerOffset,
                            blendMode = BlendMode.Multiply
                        )
                        clipRect(
                            left = xPos,
                            right = (xPos + iconSize) * animateBySelectionModel,
                        ) {
                            drawPath(path, color = primaryColor, style = Fill)
                        }
                    }
                }
            }
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                alpha = if (isSelected && videoItemSelection.isSelectionMode) 0.3f else 1f
            }
            .combinedClickableAvoidingDuplication(
                onClick = {
                    if (videoItemSelection.isSelectionMode) {
                        if(isSelected)
                            removeSelectedUri(videoItem.uri)
                        else
                            addSelectedUri(videoItem.uri)
                    } else
                        generateSubtitle()
                },
                onLongClick = enterSelectionMode,
            ),
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
private fun VideoGridItemUnSelectionModePreview(
    @PreviewParameter(GalleryUiStatePreviewParameter::class)
    galleryUiState: GalleryUiState,
) {
    PreviewAideoTheme {
        VideoGridItem(
            videoItem = galleryUiState.data.first(),
            videoItemSelection = VideoItemSelection.getDefault(),
            generateSubtitle = {},
            enterSelectionMode = {},
            addSelectedUri = {},
            removeSelectedUri = {},
        )
    }
}

@Preview
@Composable
private fun VideoGridItemSelectionModePreview(
    @PreviewParameter(GalleryUiStatePreviewParameter::class)
    galleryUiState: GalleryUiState,
) {
    PreviewAideoTheme {
        VideoGridItem(
            videoItem = galleryUiState.data.first(),
            videoItemSelection = VideoItemSelection.getDefault().apply {
                isSelectionMode = true
            },
            generateSubtitle = {},
            enterSelectionMode = {},
            addSelectedUri = {},
            removeSelectedUri = {},
        )
    }
}

@Preview
@Composable
private fun VideoGridItemSelectionModeOnSelectedPreview(
    @PreviewParameter(GalleryUiStatePreviewParameter::class)
    galleryUiState: GalleryUiState,
) {
    PreviewAideoTheme {
        VideoGridItem(
            videoItem = galleryUiState.data.first(),
            videoItemSelection = VideoItemSelection.getDefault().apply {
                isSelectionMode = true
                addSelectedUri(galleryUiState.data.first().uri)
            },
            generateSubtitle = {},
            enterSelectionMode = {},
            addSelectedUri = {},
            removeSelectedUri = {},
        )
    }
}