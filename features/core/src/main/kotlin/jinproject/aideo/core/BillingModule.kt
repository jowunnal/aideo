package jinproject.aideo.core

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import jinproject.aideo.design.R
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 결제 관리 모듈 클래스
 *
 * @param activity 결제에 필요한 컨텍스트, 내부적으로 AppicationContext 참조하여 사용
 * @param coroutineScope 코루틴 호환하여 실행될 코루틴 스쿠프
 * @property isReady 결제를 수행하기에 준비된 상태인지의 여부
 */
@Immutable
class BillingModule(
    private val activity: Activity,
    private val coroutineScope: CoroutineScope,
) {
    var isReady: Boolean = false

    private val purChasedUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    for (purchase in purchases) {
                        coroutineScope.launch(Dispatchers.IO) {
                            handlePurchase(
                                purchase = purchase,
                                isConsumable = Product.findProductById(purchase.products.first())!!.isConsumable
                            )
                        }
                    }
                }
            }

            else -> {
                failListener.call(billingResult.responseCode)
                Timber.e(billingResult.debugMessage)
            }
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(activity)
        .setListener(purChasedUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    init {
        initBillingClient()
    }

    /**
     * 구글 결제 백엔드와의 연결 활성화
     */
    fun initBillingClient() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    readyListener.call(this@BillingModule)
                    isReady = true
                } else {
                    failListener.call(billingResult.responseCode)
                    isReady = false
                }
            }

            override fun onBillingServiceDisconnected() {
                isReady = false
            }
        })
    }

    private val successListener: BillingListener<Purchase> = BillingListener()
    private val failListener: BillingListener<Int> = BillingListener()
    private val readyListener: BillingListener<BillingModule> = BillingListener()

    /**
     * 결제 플로우 리스너 등록
     */
    fun addBillingListener(
        onSuccess: OnSuccessListener? = null,
        onFailure: OnFailListener? = null,
        onReady: OnReadyListener? = null,
    ) {
        onSuccess?.let {
            successListener.addListener(it)
        }
        onFailure?.let {
            failListener.addListener(it)
        }
        onReady?.let {
            readyListener.addListener(it)
        }
    }

    /**
     * 결제 플로우 리스너 제거
     */
    fun removeBillingListener(
        onSuccess: OnSuccessListener? = null,
        onFailure: OnFailListener? = null,
        onReady: OnReadyListener? = null,
    ) {
        onSuccess?.let {
            successListener.removeListener(it)
        }
        onFailure?.let {
            failListener.removeListener(it)
        }
        onReady?.let {
            readyListener.removeListener(it)
        }
    }

    /**
     * 구매 가능한 상품의 상품 정보를 fetch
     *
     * @param products : 상품 목록
     * @return 상품정보 or 구매 가능한 상품이 아닌 경우 null
     * @see Product
     */
    suspend fun getPurchasableProducts(
        products: List<Product>,
    ): List<ProductDetails>? {
        val productList = products.map { product ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(product.id)
                .setProductType(product.type)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val productDetailsResult = withContext(Dispatchers.IO) {
            billingClient.queryProductDetails(params)
        }

        if (productDetailsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            return productDetailsResult.productDetailsList?.mapNotNull { productDetails ->
                val availableProduct = Product.findProductById(productDetails.productId)
                    ?: return null // 존재하지 않는 상품 또는 Product 에 없는 상품

                if (availableProduct.type == BillingClient.ProductType.SUBS) {
                    val isSupportSub =
                        billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS).responseCode == BillingClient.BillingResponseCode.OK
                    val isSupportSubUpdate =
                        billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS_UPDATE).responseCode == BillingClient.BillingResponseCode.OK

                    if (!isSupportSub || !isSupportSubUpdate)
                        return@mapNotNull null // Device's play-service 가 SUBS 를 지원하지 않음
                }

                if (availableProduct.isConsumable)
                    productDetails
                else {
                    val purchasedHistory = queryPurchaseAsync(availableProduct.type)

                    if (purchasedHistory.find { it.products.contains(productDetails.productId) } != null)
                        null // 구매 기록이 있어, 더 이상 구매할 수 없음.
                    else
                        productDetails
                }
            }
        } else {
            failListener.call(productDetailsResult.billingResult.responseCode)
            return null
        }
    }

    suspend fun queryProductDetails(
        product: Product,
    ): ProductDetails? {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(product.id)
            .setProductType(product.type)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        val productDetailsResult = withContext(Dispatchers.IO) {
            billingClient.queryProductDetails(params)
        }

        if (productDetailsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            return productDetailsResult.productDetailsList?.find { productDetails ->
                if (productDetails.productType == BillingClient.ProductType.SUBS) {
                    val isSupportSub =
                        billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS).responseCode == BillingClient.BillingResponseCode.OK
                    val isSupportSubUpdate =
                        billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS_UPDATE).responseCode == BillingClient.BillingResponseCode.OK

                    if (!isSupportSub || !isSupportSubUpdate)
                        return null // Device's play-service 가 SUBS 를 지원하지 않음
                }

                return productDetails
            }
        } else {
            failListener.call(productDetailsResult.billingResult.responseCode)
            return null
        }
    }

    /**
     * 결제 플로우 실행
     *
     * @param productDetails : 구매 가능한 구독 상품
     * @param offerIdx : 구독 상품의 선택된 옵션에 대한 index, 구독 상품이 아닌 경우 return
     * @see getPurchasableProducts
     */
    fun purchaseSubscription(productDetails: ProductDetails, offerIdx: Int) {
        val offerToken = productDetails.subscriptionOfferDetails
            ?.get(offerIdx)
            ?.offerToken ?: return

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    fun purchaseOneTime(productDetails: ProductDetails) {
        val offerToken = productDetails.oneTimePurchaseOfferDetails?.offerToken ?: return

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    /**
     * 구매 처리
     *
     * 구매된 상태에 한하여,
     * 소비성 제품이면, 소비 처리후 콜백 실행
     * 소비성 제품이 아니고, 승인되지 않은 경우, 승인 처리후 콜백 실행
     *
     * @param purchase 구매 상품
     * @param isConsumable 소비성 제품 인지 아닌지
     */
    private suspend fun handlePurchase(purchase: Purchase, isConsumable: Boolean) =
        withContext(Dispatchers.IO) {
            if (purchase.isPurchased()) {
                if (isConsumable) {
                    val consumeParams = ConsumeParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()

                    billingClient.consumeAsync(consumeParams) { result, _ ->
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            successListener.call(purchase)
                        } else {
                            failListener.call(result.responseCode)
                        }
                    }
                } else {
                    if (!purchase.isAcknowledged) {
                        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)

                        billingClient.acknowledgePurchase(acknowledgePurchaseParams.build()) { result ->
                            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                                successListener.call(purchase)
                            } else {
                                failListener.call(result.responseCode)
                            }
                        }
                    }
                }
            }
        }

    /**
     * 구매 목록 가져오기
     * @param type : 상품 타입(InApp, Sub)
     * @return 구매 목록 반환
     */
    suspend fun queryPurchaseAsync(type: String): List<Purchase> {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(type)

        val queryAsyncResult = withContext(Dispatchers.IO) {
            billingClient.queryPurchasesAsync(params.build())
        }

        return if (queryAsyncResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK)
            queryAsyncResult.purchasesList
        else
            emptyList()
    }


    /**
     * 구매 목록 내에서, 결제 승인 처리
     * @param purchaseList 구매 목록
     */
    suspend fun approvePurchased(purchaseList: List<Purchase>) {
        purchaseList.forEach { purchase ->
            handlePurchase(
                purchase = purchase,
                isConsumable = Product.findProductById(purchase.products.first())!!.isConsumable,
            )
        }
    }

    /**
     * 구매 목록내에서 특정 상품이 구매 및 승인 되었는지 확인
     * @param product : 상품
     * @return 구매 후 승인 되었다면 true, 없으면 false
     */
    suspend fun isProductPurchased(product: Product): Boolean {
        return queryPurchaseAsync(product.type).filter { purchase ->
            purchase.isPurchasedAndAcknowledged()
        }.any { purchase ->
            purchase.products.contains(product.id)
        }
    }

    interface BillingCallback<T> {
        fun call(c: T)
    }

    interface OnReadyListener : BillingCallback<BillingModule>

    interface OnSuccessListener : BillingCallback<Purchase>

    interface OnFailListener : BillingCallback<Int>

    class BillingListener<T> {
        private val callbacks: CopyOnWriteArraySet<BillingCallback<T>> = CopyOnWriteArraySet()

        fun addListener(c: BillingCallback<T>) {
            callbacks.add(c)
        }

        fun removeListener(c: BillingCallback<T>) {
            callbacks.remove(c)
        }

        fun call(c: T) {
            callbacks.onEach { it.call(c) }
        }
    }

    /**
     * 앱에서 제공하는 상품
     */
    enum class Product(
        @field:StringRes val displayResId: Int,
        val id: String,
        val type: String,
        val isConsumable: Boolean
    ) {
        REMOVE_AD(R.string.billing_product_remove_ad, "remove_ad", BillingClient.ProductType.SUBS, false),
        DONATION(R.string.billing_product_donation, "donation", BillingClient.ProductType.INAPP, true);

        companion object {
            fun findProductById(id: String): Product? = entries.find { value -> value.id == id }
        }
    }
}

/**
 * @return 구매는 완료됬지만, 승인 되지 않은 경우 true 아니면 false
 */
fun Purchase.isPurchasedButAcknowledged(): Boolean =
    isPurchased() && !isAcknowledged

/**
 * @return 구매와 승인이 모두 완료된 경우 true 아니면 false
 */
fun Purchase.isPurchasedAndAcknowledged(): Boolean =
    isPurchased() && isAcknowledged

fun Purchase.isPurchased(): Boolean = purchaseState == Purchase.PurchaseState.PURCHASED

fun Purchase.toProduct(): BillingModule.Product? =
    BillingModule.Product.findProductById(products.first())