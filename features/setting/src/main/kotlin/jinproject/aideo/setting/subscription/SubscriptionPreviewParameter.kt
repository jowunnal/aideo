package jinproject.aideo.setting.subscription

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import jinproject.aideo.core.BillingModule
import jinproject.aideo.design.R
import java.time.LocalDateTime
import java.time.ZoneOffset

class SubscriptionPreviewParameter : PreviewParameterProvider<SubscriptionUiState> {
    override val values: Sequence<SubscriptionUiState>
        get() = sequenceOf(
            SubscriptionUiState.Loading,
            SubscriptionUiState.UnSupportedProduct,
            SubscriptionUiState.UnSubscribed(
                product = BillingModule.Product.REMOVE_AD,
                formattedPrice = "4,900원",
                productDetails = null
            ),
            SubscriptionUiState.Subscribing(
                id = BillingModule.Product.REMOVE_AD.id,
                planNameResId = R.string.billing_product_remove_ad,
                price = "4,900원",
                purchaseTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
                billingPeriod = "P1M",
                isAutoRenewing = true,
            )
        )
}