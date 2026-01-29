package jinproject.aideo.app.ad

import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.annotation.concurrent.Immutable

class AdMobManager {
    private var _isAdViewRemoved = MutableStateFlow(true)
    val isAdviewRemoved = _isAdViewRemoved.asStateFlow()

    fun initAdView() {
        updateIsAdViewRemoved(false)

        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder().setTestDeviceIds(listOf(TEST_DEVICE_HASHED_ID)).build()
        )
    }

    fun updateIsAdViewRemoved(boolean: Boolean) = _isAdViewRemoved.update { boolean }

    companion object {
        const val TEST_DEVICE_HASHED_ID = "24162C621C2545162630B8989484FD6E"
    }
}