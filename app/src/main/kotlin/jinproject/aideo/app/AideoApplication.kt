package jinproject.aideo.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Color
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.HiltAndroidApp
import jinproject.aideo.gallery.ForegroundObserver
import jinproject.aideo.gallery.TranscribeService

@HiltAndroidApp
class AideoApplication : Application(), ForegroundObserver {
    override var isForeground: Boolean = false

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel(
            channelId = "Aideo Channel",
            channelName = "Aideo 알림 채널",
            descriptionText = "Aideo 알림 채널 입니다."
        )

        createNotificationChannel(
            channelId = TranscribeService.NOTIFICATION_CHANNEL_ID,
            channelName = "자막 생성 알림 채널",
            descriptionText = "Aideo 앱의 자막 생성을 위한 채널 입니다.",
            importance = NotificationManager.IMPORTANCE_HIGH,
        )

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    private fun createNotificationChannel(
        channelId: String,
        channelName: String,
        descriptionText: String,
        importance: Int = NotificationManager.IMPORTANCE_DEFAULT
    ) {
        val channel = NotificationChannel(channelId, channelName, importance).apply {
            description = descriptionText
            enableVibration(true)
            setShowBadge(true)
            enableLights(true)
            lightColor = Color.BLUE
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}