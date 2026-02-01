package jinproject.aideo.setting.subscription

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import jinproject.aideo.design.R
import jinproject.aideo.design.component.HorizontalSpacer
import jinproject.aideo.design.component.HorizontalWeightSpacer
import jinproject.aideo.design.component.VerticalSpacer
import jinproject.aideo.design.component.button.DefaultButton
import jinproject.aideo.design.component.button.clickableAvoidingDuplication
import jinproject.aideo.design.component.text.DescriptionLargeText
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.component.text.DescriptionSmallText
import jinproject.aideo.design.theme.AideoTheme
import jinproject.aideo.setting.subscription.component.SubInfoCard
import jinproject.aideo.setting.subscription.component.SubStatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionManagementScreen(
    uiState: SubscriptionUiState.Subscribing,
) {
    SubStatusCard(uiState)
    VerticalSpacer(16.dp)
    SubInfoCard(uiState)
    VerticalSpacer(16.dp)
    GooglePlayManagementCard(uiState.id)
    VerticalSpacer(16.dp)
    HelpCard()
}

@Composable
private fun GooglePlayManagementCard(
    productId: String,
    context: Context = LocalContext.current,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .clickableAvoidingDuplication {
                val url =
                    "https://play.google.com/store/account/subscriptions?sku=$productId&package=jinproject.aideo.app"

                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW
                    ).apply { data = url.toUri() }
                )
            }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_playstore),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(20.dp),
        )
        HorizontalSpacer(8.dp)
        DescriptionSmallText(
            text = stringResource(R.string.subscription_management_google_play),
            color = MaterialTheme.colorScheme.surfaceVariant,
        )
        HorizontalWeightSpacer(1f)
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_right_small),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun HelpCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                shape = RoundedCornerShape(16.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DescriptionSmallText(
            text = stringResource(R.string.subscription_management_help_title),
            color = MaterialTheme.colorScheme.inversePrimary
        )
        DescriptionSmallText(
            text = stringResource(R.string.subscription_management_help_desc),
            color = MaterialTheme.colorScheme.inversePrimary.copy(0.7f)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SubscriptionManagementScreenPreview() {
    AideoTheme {
        SubscriptionManagementScreen(
            uiState = SubscriptionUiState.Subscribing.getDefault(),
        )
    }
}