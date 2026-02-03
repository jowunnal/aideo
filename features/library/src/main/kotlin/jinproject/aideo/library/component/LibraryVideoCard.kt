package jinproject.aideo.library.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.unit.dp
import jinproject.aideo.design.R
import jinproject.aideo.design.component.SubcomposeAsyncImageWithPreview
import jinproject.aideo.design.component.button.combinedClickableAvoidingDuplication
import jinproject.aideo.design.component.text.DescriptionSmallText
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.design.utils.fillBounds
import jinproject.aideo.library.VideoItemSelection
import jinproject.aideo.library.model.LibraryVideoItem

@Composable
internal fun LibraryVideoCard(
    videoItem: LibraryVideoItem,
    videoItemSelection: VideoItemSelection,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isSelected by remember(videoItemSelection) {
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
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
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
                        if (isSelected)
                            videoItemSelection.removeSelectedUri(videoItem.uri)
                        else
                            videoItemSelection.addSelectedUri(videoItem.uri)
                    } else
                        onClick()
                },
                onLongClick = {
                    videoItemSelection.updateIsSelectionMode(true)
                },
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImageWithPreview(
                placeHolderPreview = R.drawable.test,
                model = videoItem.thumbnailPath,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .drawWithContent {
                        drawContent()
                        drawRect(Color.Black.copy(alpha = 0.3f))
                    },
                contentScale = ContentScale.FillWidth,
            )

            Image(
                imageVector = ImageVector.vectorResource(R.drawable.ic_playback_play),
                contentDescription = stringResource(R.string.content_desc_play_video),
                modifier = Modifier
                    .size(40.dp)
                    .drawBehind {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.2f),
                            radius = 28.dp.toPx()
                        )
                    },
                colorFilter = ColorFilter.tint(Color.White)
            )
        }

        DescriptionSmallText(
            text = videoItem.date,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Preview
@Composable
private fun LibraryVideoCardPreview() {
    PreviewAideoTheme {
        LibraryVideoCard(
            videoItem = LibraryVideoItem(
                uri = "test",
                id = 1,
                thumbnailPath = null,
                date = "2025.01.28"
            ),
            videoItemSelection = VideoItemSelection(),
            onClick = {},
        )
    }
}
