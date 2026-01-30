package jinproject.aideo.gallery.subs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.ProductDetails
import jinproject.aideo.core.BillingModule.Product
import jinproject.aideo.core.SnackBarMessage
import jinproject.aideo.core.utils.LocalBillingModule
import jinproject.aideo.core.utils.LocalShowSnackBar
import jinproject.aideo.design.R
import jinproject.aideo.design.component.HorizontalWeightSpacer
import jinproject.aideo.design.component.VerticalSpacer
import jinproject.aideo.design.component.bar.BackButtonRowScopeAppBar
import jinproject.aideo.design.component.button.DefaultIconButton
import jinproject.aideo.design.component.text.DescriptionAnnotatedSmallText
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.component.text.HeadlineSmallText
import jinproject.aideo.design.component.text.TitleMediumText
import jinproject.aideo.design.component.text.TitleSmallText
import jinproject.aideo.design.theme.AideoColor
import jinproject.aideo.design.theme.AideoTheme
import jinproject.aideo.gallery.subs.component.BenefitItem
import jinproject.aideo.gallery.subs.component.SubscriptionCard

@Immutable
sealed class SubscriptionUiState {
    data object Empty : SubscriptionUiState()
    data class Exists(
        val product: Product,
        val formattedPrice: String,
        val productDetails: ProductDetails?,
    ) : SubscriptionUiState() {
        companion object {
            fun getDefault(): Exists = Exists(
                product = Product.REMOVE_AD,
                formattedPrice = "4,900원",
                productDetails = null
            )
        }
    }
}

@Composable
internal fun SubscriptionScreen(
    navigatePopBackStack: () -> Unit,
    navigateToSubscriptionManagement: () -> Unit,
) {
    val billingModule = LocalBillingModule.current
    val showSnackBar = LocalShowSnackBar.current
    val context = LocalContext.current

    val uiState by produceState<SubscriptionUiState>(
        SubscriptionUiState.Empty,
        billingModule
    ) {
        val purchasableProducts =
            billingModule.getPurchasableProducts(listOf(Product.REMOVE_AD))

        value = purchasableProducts?.firstOrNull()?.let { p ->
            val product =
                Product.findProductById(p.productId) ?: return@let SubscriptionUiState.Empty
            val prisingPhase =
                p.subscriptionOfferDetails?.first()?.pricingPhases?.pricingPhaseList?.first()
                    ?: return@let SubscriptionUiState.Empty

            SubscriptionUiState.Exists(
                product = product,
                formattedPrice = prisingPhase.formattedPrice,
                productDetails = p
            )
        } ?: SubscriptionUiState.Empty
    }

    SubscriptionScreen(
        uiState = uiState,
        launchBillingFlow = {
            (uiState as? SubscriptionUiState.Exists)?.productDetails?.let { p ->
                billingModule.purchaseSubscription(
                    productDetails = p,
                    offerIdx = 0
                )
            } ?: showSnackBar.invoke(
                SnackBarMessage(
                    headerMessage = context.getString(R.string.subscription_already_purchased_header),
                    contentMessage = context.getString(R.string.subscription_already_purchased_content)
                )
            )
        },
        navigatePopBackStack = navigatePopBackStack,
        navigateToSubscriptionManagement = navigateToSubscriptionManagement,
    )
}

@Composable
private fun SubscriptionScreen(
    uiState: SubscriptionUiState,
    launchBillingFlow: () -> Unit,
    navigatePopBackStack: () -> Unit,
    navigateToSubscriptionManagement: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        SubscriptionAppBar(
            onBack = navigatePopBackStack,
            onGoToManagement = navigateToSubscriptionManagement,
        )

        VerticalSpacer(30.dp)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SubscriptionHeader()

            VerticalSpacer(32.dp)

            BenefitsSection()

            VerticalSpacer(24.dp)

            SubscriptionCard(
                planName = stringResource(R.string.subscription_monthly_plan),
                planDescription = stringResource(R.string.subscription_monthly_plan_desc),
                price = if (uiState is SubscriptionUiState.Exists)
                    uiState.formattedPrice
                else
                    stringResource(R.string.subscription_price_unavailable),
                priceUnit = stringResource(R.string.subscription_price_unit),
                notice = stringResource(R.string.subscription_auto_renewal_notice),
                buttonText = stringResource(R.string.subscription_subscribe_now),
                onSubscribeClick = {
                    launchBillingFlow()
                },
            )

            VerticalSpacer(24.dp)

            SubscriptionFooter(
                if (uiState is SubscriptionUiState.Exists)
                    uiState.product.id
                else
                    null
            )

            VerticalSpacer(32.dp)
        }
    }
}

@Composable
private fun SubscriptionAppBar(
    onBack: () -> Unit,
    onGoToManagement: () -> Unit,
) {
    BackButtonRowScopeAppBar(
        modifier = Modifier,
        backgroundColor = MaterialTheme.colorScheme.primary,
        onBackClick = onBack,
    ) {
        TitleMediumText(
            text = stringResource(R.string.subscription_title),
            color = MaterialTheme.colorScheme.onPrimary
        )
        HorizontalWeightSpacer(1f)
        DefaultIconButton(
            icon = R.drawable.ic_settings_outlined,
            onClick = onGoToManagement,
            iconTint = MaterialTheme.colorScheme.onPrimary,
            backgroundTint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SubscriptionHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_crown),
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .shadow(1.dp, CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            AideoColor.amber_300.color,
                            AideoColor.orange_500.color
                        )
                    ),
                    shape = CircleShape
                )
                .padding(20.dp),
            colorFilter = ColorFilter.tint(Color.White)
        )

        VerticalSpacer(16.dp)

        HeadlineSmallText(
            text = stringResource(R.string.subscription_header_title),
            textAlign = TextAlign.Center,
        )

        VerticalSpacer(8.dp)

        DescriptionMediumText(
            text = stringResource(R.string.subscription_header_desc),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BenefitsSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp)
    ) {
        TitleSmallText(
            text = stringResource(R.string.subscription_benefits_title),
        )

        VerticalSpacer(16.dp)

        BenefitItem(
            title = stringResource(R.string.subscription_benefit_ad_removal),
            description = stringResource(R.string.subscription_benefit_ad_removal_desc),
        )
    }
}

@Composable
private fun SubscriptionFooter(
    productId: String?
) {
    val footerManagedBy = stringResource(R.string.subscription_footer_managed_by)
    val footerAutoRenewal = stringResource(R.string.subscription_footer_auto_renewal)
    val footerRefundPolicy = stringResource(R.string.subscription_footer_refund_policy)

    val annotatedString = buildAnnotatedString {
        withStyle(
            style = ParagraphStyle(lineHeight = 8.sp)
        ) {
            productId?.let {
                append(footerManagedBy.substringBefore("Google Play"))
                withLink(LinkAnnotation.Url("https://play.google.com/store/account/subscriptions?sku=$productId&package=jinproject.aideo.app")) {
                    append("Google Play")
                }
                appendLine(footerManagedBy.substringAfter("Google Play"))
            } ?: appendLine(footerManagedBy)
        }
        appendLine(footerAutoRenewal)
        withStyle(
            style = ParagraphStyle(lineHeight = 8.sp)
        ) {
            appendLine(footerRefundPolicy)
        }
    }

    DescriptionAnnotatedSmallText(
        text = annotatedString,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewSubscriptionScreen(
    @PreviewParameter(SubscriptionPreviewParameter::class)
    uiState: SubscriptionUiState,
) = AideoTheme {
    SubscriptionScreen(
        uiState = uiState,
        launchBillingFlow = {},
        navigatePopBackStack = {},
        navigateToSubscriptionManagement = {},
    )
}
