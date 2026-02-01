package jinproject.aideo.gallery.gallery.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jinproject.aideo.design.R
import jinproject.aideo.design.component.VerticalSpacer
import jinproject.aideo.design.component.button.clickableAvoidingDuplication
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.component.text.TitleMediumText
import jinproject.aideo.design.utils.PreviewAideoTheme

@Composable
internal fun NewProjectCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.8f),
                        primaryColor
                    )
                )
            )
            .clickableAvoidingDuplication(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.icon_plus),
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .drawBehind {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.2f),
                        radius = 28.dp.toPx()
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.3f),
                        radius = 28.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx())
                    )
                },
            tint = Color.White
        )

        VerticalSpacer(height = 24.dp)

        TitleMediumText(
            text = stringResource(R.string.gallery_new_project),
            color = Color.White
        )

        VerticalSpacer(height = 6.dp)

        DescriptionMediumText(
            text = stringResource(R.string.gallery_new_project_desc),
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Preview
@Composable
private fun NewProjectCardPreview() {
    PreviewAideoTheme {
        NewProjectCard(
            modifier = Modifier.padding(16.dp),
            onClick = {}
        )
    }
}
