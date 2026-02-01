package jinproject.aideo.library.component

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jinproject.aideo.design.R
import jinproject.aideo.design.component.SubcomposeAsyncImageWithPreview
import jinproject.aideo.design.component.button.clickableAvoidingDuplication
import jinproject.aideo.design.component.text.DescriptionSmallText
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.library.model.LibraryVideoItem

@Composable
internal fun LibraryVideoCard(
    videoItem: LibraryVideoItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
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
            .clickableAvoidingDuplication(onClick = onClick)
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
            onClick = {}
        )
    }
}
