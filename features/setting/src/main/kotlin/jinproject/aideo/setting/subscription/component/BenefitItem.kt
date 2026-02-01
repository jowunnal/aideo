package jinproject.aideo.setting.subscription.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jinproject.aideo.design.R
import jinproject.aideo.design.component.HorizontalSpacer
import jinproject.aideo.design.component.VerticalSpacer
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.component.text.DescriptionSmallText
import jinproject.aideo.design.theme.AideoTheme

@Composable
internal fun BenefitItem(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_check_small),
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = CircleShape
                )
                .padding(4.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
        )

        HorizontalSpacer(12.dp)

        Column(modifier = Modifier.fillMaxWidth()) {
            DescriptionMediumText(
                text = title,
                color = MaterialTheme.colorScheme.onBackground
            )
            VerticalSpacer(4.dp)
            DescriptionSmallText(
                text = description,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewBenefitItem() = AideoTheme {
    BenefitItem(
        title = "광고 제거",
        description = "모든 광고가 제거되어 끊김 없는 사용 가능",
        modifier = Modifier.padding(16.dp)
    )
}
