package jinproject.aideo.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.play.core.aipacks.AiPackStateUpdateListener
import com.google.android.play.core.aipacks.model.AiPackStatus
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import jinproject.aideo.app.BuildConfig.ADMOB_REWARD_ID
import jinproject.aideo.app.ad.AdMobManager
import jinproject.aideo.app.ad.BannerAd
import jinproject.aideo.app.navigation.NavigationDefaults
import jinproject.aideo.app.navigation.NavigationGraph
import jinproject.aideo.app.navigation.isBarHasToBeShown
import jinproject.aideo.app.navigation.navigationSuiteItems
import jinproject.aideo.app.navigation.rememberRouter
import jinproject.aideo.app.update.InAppUpdateManager
import jinproject.aideo.core.BillingModule
import jinproject.aideo.core.SnackBarMessage
import jinproject.aideo.core.inference.AiModelConfig
import jinproject.aideo.core.toProduct
import jinproject.aideo.core.utils.AnalyticsEvent
import jinproject.aideo.core.utils.LocalAnalyticsLoggingEvent
import jinproject.aideo.core.utils.LocalBillingModule
import jinproject.aideo.core.utils.LocalShowRewardAd
import jinproject.aideo.core.utils.LocalShowSnackBar
import jinproject.aideo.core.utils.getAiPackManager
import jinproject.aideo.core.utils.getAiPackStates
import jinproject.aideo.core.utils.getPackStatus
import jinproject.aideo.design.component.SnackBarHostCustom
import jinproject.aideo.design.component.paddingvalues.addStatusBarPadding
import jinproject.aideo.design.theme.AideoTheme
import jinproject.aideo.player.navigateToPlayerGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        if (result.not()) {
            Toast.makeText(
                applicationContext,
                getString(jinproject.aideo.design.R.string.permission_required),
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

    private val adMobManager by lazy { AdMobManager() }

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        setContent {
            AideoTheme {
                Content()
            }
        }

        MobileAds.initialize(this@MainActivity) {
            loadRewardedAd()
        }
        firebaseAnalytics = Firebase.analytics

        inAppUpdateManager.checkUpdateIsAvailable(launcher = inAppUpdateLauncher)
        setUpBaseAiPack()
    }

    @Composable
    private fun Content(
        navController: NavHostController = rememberNavController(),
        coroutineScope: CoroutineScope = rememberCoroutineScope(),
        context: Context = LocalContext.current,
    ) {
        val snackBarHostState = remember { SnackbarHostState() }

        val snackBarChannel = remember {
            Channel<SnackBarMessage>(Channel.CONFLATED)
        }

        val showSnackBar: (SnackBarMessage) -> Unit = remember {
            { snackBarMessage: SnackBarMessage ->
                snackBarChannel.trySend(snackBarMessage)
            }
        }

        val ls =
            rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    showSnackBar(
                        SnackBarMessage(
                            headerMessage = context.getString(jinproject.aideo.design.R.string.download_started),
                            contentMessage = context.getString(jinproject.aideo.design.R.string.download_please_wait)
                        )
                    )
                } else if (result.resultCode == RESULT_CANCELED) {
                    showSnackBar(
                        SnackBarMessage(
                            headerMessage = context.getString(jinproject.aideo.design.R.string.download_cancelled),
                            contentMessage = context.getString(jinproject.aideo.design.R.string.download_cancelled_desc)
                        )
                    )
                }
            }

        LaunchedEffect(key1 = snackBarChannel) {
            snackBarChannel.receiveAsFlow()
                .distinctUntilChanged { old, new -> old.headerMessage == new.headerMessage && old.contentMessage == new.contentMessage }
                .collectLatest { snackBarMessage ->
                    snackBarHostState.currentSnackbarData?.dismiss()
                    snackBarHostState.showSnackbar(
                        message = snackBarMessage.headerMessage,
                        actionLabel = snackBarMessage.contentMessage,
                        duration = SnackbarDuration.Indefinite,
                    )
                }
        }

        DisposableEffect(Unit) {
            val onNewIntentConsumer = Consumer<Intent> {
                if (it.getBooleanExtra("deepLink", true))
                    navController.handleDeepLink(it)
                else {
                    it.getStringExtra("videoUri")?.let { uri ->
                        navController.navigateToPlayerGraph(videoUri = uri, navOptions = null)
                    }
                }
            }

            (context as ComponentActivity).addOnNewIntentListener(onNewIntentConsumer)

            val aiPackListener = AiPackStateUpdateListener { state ->
                when (state.status()) {
                    AiPackStatus.REQUIRES_USER_CONFIRMATION, AiPackStatus.WAITING_FOR_WIFI -> {
                        getAiPackManager().showConfirmationDialog(ls)
                    }

                    AiPackStatus.DOWNLOADING, AiPackStatus.TRANSFERRING -> {
                        showSnackBar(
                            SnackBarMessage(
                                headerMessage = context.getString(jinproject.aideo.design.R.string.download_ai_model_in_progress),
                                contentMessage = context.getString(jinproject.aideo.design.R.string.download_please_wait)
                            )
                        )
                    }

                    else -> {}
                }
            }
            getAiPackManager().registerListener(aiPackListener)

            onDispose {
                context.removeOnNewIntentListener(onNewIntentConsumer)
                getAiPackManager().unregisterListener(aiPackListener)
            }
        }

        val isAdViewRemoved by adMobManager.isAdviewRemoved.collectAsStateWithLifecycle()

        val billingModule = remember {
            BillingModule(
                activity = this@MainActivity,
                coroutineScope = coroutineScope,
            )
        }

        DisposableEffect(key1 = billingModule) {
            val success = object : BillingModule.OnSuccessListener {
                override fun call(c: Purchase) {
                    c.toProduct()?.let {
                        if (it == BillingModule.Product.REMOVE_AD)
                            adMobManager.updateIsAdViewRemoved(true)
                    }

                    showSnackBar(
                        SnackBarMessage(
                            headerMessage = context.getString(
                                jinproject.aideo.design.R.string.billing_purchase_completed,
                                c.products.first()
                            )
                        )
                    )
                }
            }
            val fail = object : BillingModule.OnFailListener {
                override fun call(c: Int) {
                    showSnackBar(
                        SnackBarMessage(
                            headerMessage = context.getString(jinproject.aideo.design.R.string.billing_purchase_failed),
                            contentMessage = when (c) {
                                2, 3 -> context.getString(jinproject.aideo.design.R.string.billing_error_invalid_product)
                                5, 6 -> context.getString(jinproject.aideo.design.R.string.billing_error_incorrect_product)
                                BillingClient.BillingResponseCode.USER_CANCELED -> context.getString(
                                    jinproject.aideo.design.R.string.billing_error_cancelled
                                )

                                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> context.getString(
                                    jinproject.aideo.design.R.string.billing_error_unavailable
                                )

                                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> context.getString(
                                    jinproject.aideo.design.R.string.billing_error_already_owned
                                )

                                else -> context.getString(jinproject.aideo.design.R.string.billing_error_network)
                            }
                        )
                    )
                }
            }
            val ready = object : BillingModule.OnReadyListener {
                override fun call(c: BillingModule) {
                    coroutineScope.launch(Dispatchers.Main.immediate) {
                        c.queryPurchaseAsync(BillingClient.ProductType.SUBS).also { purchasedList ->
                            c.approvePurchased(purchasedList)

                            if (!c.isProductPurchased(product = BillingModule.Product.REMOVE_AD))
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

        val navigationSuiteItemColors = NavigationSuiteItemColors(
            navigationBarItemColors = navBarItemColors,
            navigationRailItemColors = railBarItemColors,
            navigationDrawerItemColors = drawerItemColors,
        )

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
            LocalShowSnackBar provides showSnackBar,
            LocalShowRewardAd provides ::showRewardedAd,
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
                    BannerAd(
                        adsVisibility = !isAdViewRemoved,
                        modifier = Modifier.fillMaxWidth(),
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

    private var isLoadingRewardAd = false

    private fun loadRewardedAd() {
        if (isLoadingRewardAd || mRewardedAd != null)
            return

        isLoadingRewardAd = true
        RewardedAd.load(
            this,
            ADMOB_REWARD_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    mRewardedAd = null
                    isLoadingRewardAd = false
                }

                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    mRewardedAd = rewardedAd
                    isLoadingRewardAd = false

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

    private fun showRewardedAd(onResult: () -> Unit) {
        if (adMobManager.isAdviewRemoved.value) {
            onResult()
            return
        }

        mRewardedAd?.show(this) {
            onResult()
        } ?: run {
            loadRewardedAd()
            mRewardedAd?.show(this@MainActivity) {
                onResult()
            } ?: onResult()
        }
    }

    private fun setUpBaseAiPack() {
        getAiPackStates(AiModelConfig.SPEECH_BASE_PACK)
            .addOnCompleteListener { t ->
                runCatching {
                    when (t.getPackStatus(AiModelConfig.SPEECH_BASE_PACK)) {
                        AiPackStatus.CANCELED, AiPackStatus.FAILED, AiPackStatus.PENDING -> {
                            getAiPackManager().fetch(listOf(AiModelConfig.SPEECH_BASE_PACK))
                        }

                        else -> {}
                    }
                }.onFailure { t ->
                    Timber.e("error while get AI Pack Manager: ${t.message}")
                }
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

    override fun onDestroy() {
        mRewardedAd?.fullScreenContentCallback = null
        mRewardedAd = null
        super.onDestroy()
    }
}