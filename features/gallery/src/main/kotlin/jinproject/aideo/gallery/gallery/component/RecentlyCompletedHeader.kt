package jinproject.aideo.gallery.gallery.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jinproject.aideo.design.R
import jinproject.aideo.design.component.button.clickableAvoidingDuplication
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.component.text.TitleMediumText
import jinproject.aideo.design.utils.PreviewAideoTheme

@Composable
internal fun RecentlyCompletedHeader(
    modifier: Modifier = Modifier,
    onViewAllClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TitleMediumText(
            text = stringResource(R.string.gallery_recently_completed),
            color = MaterialTheme.colorScheme.onBackground,
        )

        DescriptionMediumText(
            text = stringResource(R.string.gallery_view_all),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.clickableAvoidingDuplication(onClick = onViewAllClick)
        )
    }
}

@Preview
@Composable
private fun RecentlyCompletedHeaderPreview() {
    PreviewAideoTheme {
        RecentlyCompletedHeader(
            onViewAllClick = {}
        )
    }
}
