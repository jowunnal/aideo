package jinproject.aideo.gallery.submanagement.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jinproject.aideo.core.BillingModule.Product
import jinproject.aideo.design.R
import jinproject.aideo.design.theme.AideoTheme
import java.time.LocalDateTime
import java.time.ZoneOffset
import jinproject.aideo.design.component.HorizontalDivider
import jinproject.aideo.design.component.text.DescriptionLargeText
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.component.text.DescriptionSmallText
import jinproject.aideo.gallery.submanagement.SubscriptionManagementUiState

@Composable
internal fun SubInfoCard(uiState: SubscriptionManagementUiState.Subscribing) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DescriptionLargeText(
            text = stringResource(R.string.subscription_management_info_title),
            color = MaterialTheme.colorScheme.onSurface.copy(0.9f),
        )

        InfoRow(
            icon = ImageVector.vectorResource(id = R.drawable.ic_shopping),
            title = stringResource(R.string.subscription_management_start_date),
            value = uiState.calculateNextBillingDate().startDate,
        )

        HorizontalDivider()

        InfoRow(
            icon = ImageVector.vectorResource(id = R.drawable.ic_shopping),
            title = stringResource(R.string.subscription_management_payment_method),
            value = stringResource(R.string.subscription_management_google_play_payment),
        )

        HorizontalDivider()

        if (uiState.isAutoRenewing) {
            InfoRow(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.subscription_management_auto_renewal),
                value = stringResource(R.string.subscription_management_auto_renewal_desc),
            )
        } else {
            InfoRow(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.subscription_management_one_time_payment),
                value = stringResource(R.string.subscription_management_one_time_payment_desc),
            )
        }

    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DescriptionMediumText(
                text = title,
                color = MaterialTheme.colorScheme.onSurface.copy(0.75f),
            )
            DescriptionSmallText(
                text = value,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SubInfoCardPreview() {
    AideoTheme {
        SubInfoCard(
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

@Preview(showBackground = true)
@Composable
private fun SubInfoCardNonAutoRenewingPreview() {
    AideoTheme {
        SubInfoCard(
            uiState = SubscriptionManagementUiState.Subscribing(
                id = Product.REMOVE_AD.id,
                planName = "월간 구독",
                price = "4,900원",
                purchaseTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
                billingPeriod = "P1M",
                isAutoRenewing = false,
            )
        )
    }
}