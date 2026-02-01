package jinproject.aideo.setting.subscription

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.android.billingclient.api.ProductDetails
import jinproject.aideo.core.BillingModule.Product
import jinproject.aideo.design.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Immutable
sealed class SubscriptionUiState {

    data object Loading : SubscriptionUiState()

    data object UnSupportedProduct : SubscriptionUiState()

    data class UnSubscribed(
        val product: Product,
        val formattedPrice: String,
        val productDetails: ProductDetails?,
    ) : SubscriptionUiState() {
        companion object {
            fun getDefault(): UnSubscribed = UnSubscribed(
                product = Product.REMOVE_AD,
                formattedPrice = "4,900원",
                productDetails = null,
            )
        }
    }

    data class Subscribing(
        val id: String,
        @field:StringRes val planNameResId: Int,
        val price: String,
        val purchaseTime: Long,
        val billingPeriod: String,
        val isAutoRenewing: Boolean,
    ) : SubscriptionUiState() {

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

        companion object {
            fun getDefault() = SubscriptionUiState.Subscribing(
                id = Product.REMOVE_AD.id,
                planNameResId = R.string.billing_product_remove_ad,
                price = "4,900원",
                purchaseTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
                billingPeriod = "P1M",
                isAutoRenewing = true,
            )
        }
    }
}