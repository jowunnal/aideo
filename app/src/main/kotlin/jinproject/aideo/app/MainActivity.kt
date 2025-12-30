package jinproject.aideo.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItemColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import jinproject.aideo.app.BuildConfig.ADMOB_REWARD_ID
import jinproject.aideo.app.ad.AdMobManager
import jinproject.aideo.app.navigation.NavigationDefaults
import jinproject.aideo.app.navigation.NavigationGraph
import jinproject.aideo.app.navigation.isBarHasToBeShown
import jinproject.aideo.app.navigation.navigationSuiteItems
import jinproject.aideo.app.navigation.rememberRouter
import jinproject.aideo.app.update.InAppUpdateManager
import jinproject.aideo.core.utils.AnalyticsEvent
import jinproject.aideo.core.BillingModule
import jinproject.aideo.core.utils.LocalAnalyticsLoggingEvent
import jinproject.aideo.core.utils.LocalBillingModule
import jinproject.aideo.core.SnackBarMessage
import jinproject.aideo.core.toProduct
import jinproject.aideo.design.component.SnackBarHostCustom
import jinproject.aideo.design.component.paddingvalues.addStatusBarPadding
import jinproject.aideo.design.theme.AideoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        if (result.not()) {
            Toast.makeText(
                applicationContext,
                "권한이 필요합니다.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private var mRewardedAd: RewardedAd? = null

    private val inAppUpdateLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            inAppUpdateManager.inAppUpdatingLauncherResult(result)
        }

    private val inAppUpdateManager by lazy {
        InAppUpdateManager(
            context = this
        )
    }

    private val adMobManager by lazy { AdMobManager(this) }

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        firebaseAnalytics = Firebase.analytics

        setContent {
            AideoTheme {
                Content()
            }
        }
        inAppUpdateManager.checkUpdateIsAvailable(launcher = inAppUpdateLauncher)
    }

    @Composable
    private fun Content(
        navController: NavHostController = rememberNavController(),
        coroutineScope: CoroutineScope = rememberCoroutineScope(),
    ) {
        val snackBarHostState = remember { SnackbarHostState() }

        val snackBarChannel = remember {
            Channel<SnackBarMessage>(Channel.CONFLATED)
        }

        LaunchedEffect(key1 = snackBarChannel) {
            snackBarChannel.receiveAsFlow().collect { snackBarMessage ->
                snackBarHostState.currentSnackbarData?.let {
                    snackBarHostState.showSnackbar(
                        message = snackBarMessage.headerMessage,
                        actionLabel = snackBarMessage.contentMessage,
                        duration = SnackbarDuration.Indefinite,
                    )
                }
            }
        }

        val isAdViewRemoved by adMobManager.isAdviewRemoved.collectAsStateWithLifecycle()

        val showSnackBar = { snackBarMessage: SnackBarMessage ->
            snackBarChannel.trySend(snackBarMessage)
        }

        val billingModule = remember {
            BillingModule(
                activity = this,
                coroutineScope = coroutineScope,
            )
        }

        DisposableEffect(key1 = billingModule) {
            val success = object : BillingModule.OnSuccessListener {
                override fun onSuccess(purchase: Purchase) {
                    purchase.toProduct()?.let {
                        if (it == BillingModule.Product.AD_REMOVE)
                            adMobManager.updateIsAdViewRemoved(true)
                    }

                    showSnackBar(
                        SnackBarMessage(
                            headerMessage = "[${purchase.products.first()}] 상품의 구매가 완료되었어요."
                        )
                    )
                }
            }
            val fail = object : BillingModule.OnFailListener {
                override fun onFailure(errorCode: Int) {
                    showSnackBar(
                        SnackBarMessage(
                            headerMessage = "구매에 실패했어요.",
                            contentMessage = when (errorCode) {
                                2, 3 -> "유효하지 않은 상품이에요."
                                5, 6 -> "올바르지 않은 상품이에요."
                                BillingClient.BillingResponseCode.USER_CANCELED -> "구매가 취소되었어요."
                                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "유효하지 않은 상품이에요."
                                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "이미 보유하고 있는 상품이에요."
                                else -> "네트워크 오류가 발생했어요."
                            }
                        )
                    )
                }
            }
            val ready = object : BillingModule.OnReadyListener {
                override fun onReady(billingModule: BillingModule) {
                    coroutineScope.launch(Dispatchers.Main.immediate) {
                        billingModule.queryPurchaseAsync().also { purchasedList ->
                            billingModule.approvePurchased(purchasedList)

                            if (!billingModule.isProductPurchased(
                                    product = BillingModule.Product.AD_REMOVE,
                                    purchaseList = purchasedList,
                                )
                            )
                                adMobManager.initAdView()
                        }
                    }
                }
            }

            billingModule.addBillingListener(
                onSuccess = success,
                onFailure = fail,
                onReady = ready,
            )

            onDispose {
                billingModule.removeBillingListener(
                    onSuccess = success,
                    onFailure = fail,
                    onReady = ready,
                )
            }
        }

        val navBarItemColors = NavigationBarItemDefaults.colors(
            indicatorColor = NavigationDefaults.navigationIndicatorColor()
        )
        val railBarItemColors = NavigationRailItemDefaults.colors(
            indicatorColor = NavigationDefaults.navigationIndicatorColor()
        )
        val drawerItemColors = NavigationDrawerItemDefaults.colors()

        val navigationSuiteItemColors = remember {
            NavigationSuiteItemColors(
                navigationBarItemColors = navBarItemColors,
                navigationRailItemColors = railBarItemColors,
                navigationDrawerItemColors = drawerItemColors,
            )
        }

        val router = rememberRouter(navController = navController)
        val currentDestination by rememberUpdatedState(newValue = router.currentDestination)

        val layoutType by rememberUpdatedState(
            newValue = if (!currentDestination.isBarHasToBeShown())
                NavigationSuiteType.None
            else
                NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
        )

        CompositionLocalProvider(
            LocalTonalElevationEnabled provides false,
            LocalAnalyticsLoggingEvent provides ::loggingAnalyticsEvent,
            LocalBillingModule provides billingModule,
        ) {
            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    navigationSuiteItems(
                        currentDestination = currentDestination,
                        itemColors = navigationSuiteItemColors,
                        onClick = { topLevelRoute ->
                            router.navigateTopLevelDestination(topLevelRoute)
                        }
                    )
                },
                layoutType = layoutType,
                containerColor = Color.Transparent,
                contentColor = Color.Transparent,
                navigationSuiteColors = NavigationSuiteDefaults.colors(
                    navigationBarContainerColor = NavigationDefaults.containerColor(),
                    navigationBarContentColor = NavigationDefaults.contentColor(),
                    navigationRailContainerColor = NavigationDefaults.containerColor(),
                    navigationRailContentColor = NavigationDefaults.contentColor(),
                    navigationDrawerContainerColor = NavigationDefaults.containerColor(),
                    navigationDrawerContentColor = NavigationDefaults.contentColor(),
                ),
            ) {
                Column(
                    modifier = Modifier.addStatusBarPadding()
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { context ->
                            AdView(context).apply {
                                setAdSize(AdSize.BANNER)
                                adUnitId = BuildConfig.ADMOB_UNIT_ID
                                loadAd(AdRequest.Builder().build())
                            }
                        },
                        update = {
                            it.visibility = if (isAdViewRemoved) View.GONE else View.VISIBLE
                        }
                    )

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        snackbarHost = {
                            SnackBarHostCustom(
                                headerMessage = snackBarHostState.currentSnackbarData?.visuals?.message
                                    ?: "",
                                contentMessage = snackBarHostState.currentSnackbarData?.visuals?.actionLabel
                                    ?: "",
                                snackBarHostState = snackBarHostState,
                                disMissSnackBar = { snackBarHostState.currentSnackbarData?.dismiss() })
                        }
                    ) { paddingValues ->
                        NavigationGraph(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    bottom = paddingValues.calculateBottomPadding(),
                                    start = paddingValues.calculateStartPadding(LayoutDirection.Rtl),
                                    end = paddingValues.calculateStartPadding(LayoutDirection.Rtl),
                                ),
                            router = router,
                            showRewardedAd = { onResult ->
                                showRewardedAd(onResult)
                            },
                            showSnackBar = { snackBarMessage ->
                                showSnackBar(snackBarMessage)
                            },
                        )
                    }
                }
            }
        }
    }

    private fun loggingAnalyticsEvent(event: AnalyticsEvent) {
        if (!BuildConfig.DEBUG)
            firebaseAnalytics.logEvent(event.eventName) {
                event.logEvent(this)
            }
    }

    private fun loadRewardedAd() {
        RewardedAd.load(
            this,
            ADMOB_REWARD_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    mRewardedAd = null
                }

                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    mRewardedAd = rewardedAd
                    mRewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            mRewardedAd = null
                            loadRewardedAd()
                        }

                        override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                            mRewardedAd = null
                        }

                        override fun onAdClicked() {}
                        override fun onAdImpression() {}
                        override fun onAdShowedFullScreenContent() {}
                    }
                }
            })
    }

    private fun showRewardedAd(onResult: () -> Unit, recursiveTimes: Int = 2) {
        if (recursiveTimes > 0)
            mRewardedAd?.show(this) {
                onResult()
            } ?: run {
                loadRewardedAd()
                showRewardedAd(onResult = onResult, recursiveTimes = recursiveTimes - 1)
            }
    }

    override fun onResume() {
        super.onResume()

        inAppUpdateManager.checkUpdateIsDownloaded()
        requestPermission()
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}