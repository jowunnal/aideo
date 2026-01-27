package jinproject.aideo.gallery.submanagement

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import jinproject.aideo.core.BillingModule.Product
import jinproject.aideo.core.isPurchasedAndAcknowledged
import jinproject.aideo.core.toProduct
import jinproject.aideo.core.utils.LocalBillingModule
import jinproject.aideo.design.R
import jinproject.aideo.design.component.HorizontalSpacer
import jinproject.aideo.design.component.HorizontalWeightSpacer
import jinproject.aideo.design.component.VerticalSpacer
import jinproject.aideo.design.component.bar.BackButtonTitleAppBar
import jinproject.aideo.design.component.button.DefaultButton
import jinproject.aideo.design.component.button.clickableAvoidingDuplication
import jinproject.aideo.design.component.text.DescriptionLargeText
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.component.text.DescriptionSmallText
import jinproject.aideo.design.theme.AideoTheme
import jinproject.aideo.gallery.submanagement.component.SubInfoCard
import jinproject.aideo.gallery.submanagement.component.SubStatusCard
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Immutable
sealed class SubscriptionManagementUiState {
    data object None : SubscriptionManagementUiState()
    data class Subscribing(
        val id: String,
        @field:StringRes val planNameResId: Int,
        val price: String,
        val purchaseTime: Long,
        val billingPeriod: String,
        val isAutoRenewing: Boolean,
    ) : SubscriptionManagementUiState() {

        fun calculateNextBillingDate(): BillingDate {
            val purchaseDate = Instant.ofEpochMilli(purchaseTime)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            val period = Period.parse(billingPeriod)
            val today = LocalDate.now()

            var nextBillingDate = purchaseDate.plus(period)

            while (nextBillingDate <= today) {
                nextBillingDate = nextBillingDate.plus(period)
            }

            return BillingDate(
                startDate = nextBillingDate.minus(period).format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd")
                ),
                endDate = nextBillingDate.format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd")
                )
            )
        }

        data class BillingDate(
            val startDate: String,
            val endDate: String,
        )
    }
}

@Composable
internal fun SubscriptionManagementScreen(
    navigatePopBackStack: () -> Unit,
    navigateToSubscription: () -> Unit,
) {
    val billingModule = LocalBillingModule.current

    val uiState by produceState<SubscriptionManagementUiState>(
        SubscriptionManagementUiState.None,
        billingModule
    ) {
        val subPurchase = billingModule.queryPurchaseAsync(Product.REMOVE_AD.type).firstOrNull()

        val productDetails = if (subPurchase?.isPurchasedAndAcknowledged() ?: false)
            billingModule.queryProductDetails(subPurchase.toProduct()!!)
        else
            null

        value = productDetails?.let { p ->
            val product = Product.findProductById(p.productId)!!
            val subscriptionOfferDetails = p.subscriptionOfferDetails!!.first()
            val prisingPhase = subscriptionOfferDetails.pricingPhases.pricingPhaseList.first()

            SubscriptionManagementUiState.Subscribing(
                id = product.id,
                planNameResId = product.displayResId,
                price = prisingPhase.formattedPrice,
                purchaseTime = subPurchase!!.purchaseTime,
                billingPeriod = prisingPhase.billingPeriod,
                isAutoRenewing = subPurchase.isAutoRenewing,
            )
        } ?: SubscriptionManagementUiState.None
    }

    SubscriptionManagementScreen(
        uiState = uiState,
        navigatePopBackStack = navigatePopBackStack,
        navigateToSubscribe = navigateToSubscription,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionManagementScreen(
    uiState: SubscriptionManagementUiState,
    navigatePopBackStack: () -> Unit,
    navigateToSubscribe: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        BackButtonTitleAppBar(
            title = stringResource(R.string.subscription_management_title),
            backgroundColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            onBackClick = navigatePopBackStack,
        )

        VerticalSpacer(12.dp)

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState is SubscriptionManagementUiState.Subscribing) {
                SubStatusCard(uiState)

                SubInfoCard(uiState)

                GooglePlayManagementCard(
                    uiState.id
                )
            } else {
                NoneSubscribedContent(navigateToSubscribe = navigateToSubscribe)
            }

            HelpCard()
        }
    }
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalWeightSpacer(1f)
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_right_small),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.surfaceVariant,
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

@Composable
private fun NoneSubscribedContent(
    navigateToSubscribe: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_crown),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(0.2f))
                .padding(16.dp),
        )

        VerticalSpacer(16.dp)

        DescriptionLargeText(
            text = stringResource(R.string.subscription_management_no_subscription),
            color = MaterialTheme.colorScheme.onSurface.copy(0.9f)
        )

        VerticalSpacer(8.dp)

        DescriptionMediumText(
            text = stringResource(R.string.subscription_management_no_subscription_desc),
            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
        )

        VerticalSpacer(24.dp)

        DefaultButton(
            onClick = navigateToSubscribe,
            backgroundColor = MaterialTheme.colorScheme.primary
        ) {
            DescriptionMediumText(
                text = stringResource(R.string.subscription_management_view_plans),
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SubscriptionManagementScreenPreview(
    @PreviewParameter(SubManagementPreviewParameter::class)
    uiState: SubscriptionManagementUiState
) {
    AideoTheme {
        SubscriptionManagementScreen(
            uiState = uiState,
            navigatePopBackStack = {},
            navigateToSubscribe = {},
        )
    }
}