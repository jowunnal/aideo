package jinproject.aideo.gallery.submanagement.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jinproject.aideo.core.BillingModule.Product
import jinproject.aideo.design.R
import jinproject.aideo.design.theme.AideoTheme
import java.time.LocalDateTime
import java.time.ZoneOffset
import jinproject.aideo.design.component.VerticalSpacer
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.component.text.TitleMediumText
import jinproject.aideo.design.theme.AideoColor
import jinproject.aideo.gallery.submanagement.SubscriptionManagementUiState

@Composable
internal fun SubStatusCard(
    uiState: SubscriptionManagementUiState.Subscribing,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = AideoColor.amber_300.color,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(8.dp),
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TitleMediumText(
                    text = stringResource(R.string.subscription_management_premium_member),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = AideoColor.blue.color,
                        modifier = Modifier.size(16.dp),
                    )
                    DescriptionMediumText(
                        text = stringResource(R.string.subscription_management_active_status),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    )
                }
            }
        }

        VerticalSpacer(16.dp)


        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DescriptionMediumText(
                    text = uiState.planName,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                )

                DescriptionMediumText(
                    text = uiState.price,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DescriptionMediumText(
                    text = stringResource(R.string.subscription_management_next_billing),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                )

                DescriptionMediumText(
                    text = uiState.calculateNextBillingDate().endDate,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SubStatusCardPreview() {
    AideoTheme {
        SubStatusCard(
            uiState = SubscriptionManagementUiState.Subscribing(
                id = Product.REMOVE_AD.id,
                planName = "월간 구독",
                price = "4,900원",
                purchaseTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
                billingPeriod = "P1M",
                isAutoRenewing = true,
            )
        )
    }
}