package jinproject.aideo.gallery.subs.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jinproject.aideo.design.R
import jinproject.aideo.design.component.HorizontalSpacer
import jinproject.aideo.design.component.HorizontalWeightSpacer
import jinproject.aideo.design.component.VerticalSpacer
import jinproject.aideo.design.component.button.DefaultButton
import jinproject.aideo.design.component.text.DescriptionSmallText
import jinproject.aideo.design.component.text.HeadlineMediumText
import jinproject.aideo.design.component.text.TitleMediumText
import jinproject.aideo.design.component.text.TitleSmallText
import jinproject.aideo.design.theme.AideoColor
import jinproject.aideo.design.theme.AideoTheme

@Composable
internal fun SubscriptionCard(
    planName: String,
    planDescription: String,
    price: String,
    priceUnit: String,
    notice: String,
    buttonText: String,
    onSubscribeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_crown),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(AideoColor.orange_500.color)
            )
            HorizontalSpacer(8.dp)
            TitleMediumText(
                text = planName,
                color = MaterialTheme.colorScheme.onPrimary
            )
            HorizontalWeightSpacer(1f)
            HeadlineMediumText(
                text = price,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        VerticalSpacer(12.dp)
        Row(modifier = Modifier.fillMaxWidth()) {
            DescriptionSmallText(
                text = planDescription,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            )
            HorizontalWeightSpacer(1f)
            DescriptionSmallText(
                text = priceUnit,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            )
        }

        VerticalSpacer(16.dp)

        DescriptionSmallText(
            text = notice,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.onPrimary.copy(0.15f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        )

        VerticalSpacer(16.dp)

        DefaultButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onSubscribeClick,
            backgroundColor = Color.White,
            shape = RoundedCornerShape(12.dp),
            contentPaddingValues = PaddingValues(vertical = 12.dp)
        ) {
            TitleSmallText(
                text = buttonText,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSubscriptionCard() = AideoTheme {
    SubscriptionCard(
        planName = "월간 구독",
        planDescription = "매달 자동 갱신",
        price = "4,900원",
        priceUnit = "/ 월",
        notice = "자동 갱신: 구독은 매달 자동으로 갱신되며, 언제든지 취소할 수 있습니다.",
        buttonText = "지금 구독하기",
        onSubscribeClick = {},
        modifier = Modifier.padding(16.dp)
    )
}
