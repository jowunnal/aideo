package jinproject.aideo.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.play.core.splitcompat.SplitCompat
import dagger.hilt.android.HiltAndroidApp
import jinproject.aideo.app.BuildConfig
import jinproject.aideo.design.R
import timber.log.Timber
import jinproject.aideo.gallery.ForegroundObserver
import jinproject.aideo.gallery.TranscribeService

@HiltAndroidApp
class AideoApplication : Application(), ForegroundObserver {
    override var isForeground: Boolean = false

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        SplitCompat.install(this)
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        createNotificationChannel(
            channelId = "Aideo Channel",
            channelName = getString(R.string.notification_channel_aideo_name),
            descriptionText = getString(R.string.notification_channel_aideo_desc)
        )

        createNotificationChannel(
            channelId = TranscribeService.NOTIFICATION_CHANNEL_ID,
            channelName = getString(R.string.notification_channel_transcribe_name),
            descriptionText = getString(R.string.notification_channel_transcribe_desc),
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