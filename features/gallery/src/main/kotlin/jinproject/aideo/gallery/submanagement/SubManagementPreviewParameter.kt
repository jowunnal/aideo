package jinproject.aideo.gallery.submanagement

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import jinproject.aideo.core.BillingModule.Product
import jinproject.aideo.design.R
import java.time.LocalDateTime
import java.time.ZoneOffset

internal class SubManagementPreviewParameter: PreviewParameterProvider<SubscriptionManagementUiState> {
    override val values: Sequence<SubscriptionManagementUiState> get() = sequenceOf(
        SubscriptionManagementUiState.None,
        SubscriptionManagementUiState.Subscribing(
            id = Product.REMOVE_AD.id,
            planNameResId = R.string.billing_product_remove_ad,
            price = "4,900원",
            purchaseTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
            billingPeriod = "P1M",
            isAutoRenewing = true,
        )
    )
}