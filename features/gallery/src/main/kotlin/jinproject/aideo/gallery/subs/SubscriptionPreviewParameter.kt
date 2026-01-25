package jinproject.aideo.gallery.subs

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import jinproject.aideo.core.BillingModule

class SubscriptionPreviewParameter : PreviewParameterProvider<SubscriptionUiState> {
    override val values: Sequence<SubscriptionUiState>
        get() = sequenceOf(
            SubscriptionUiState.Empty,
            SubscriptionUiState.Exists(
                product = BillingModule.Product.REMOVE_AD,
                formattedPrice = "4,900원"
            )
        )
}