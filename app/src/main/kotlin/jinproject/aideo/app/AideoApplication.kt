package jinproject.aideo.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.play.core.splitcompat.SplitCompat
import dagger.hilt.android.HiltAndroidApp
import jinproject.aideo.core.ForegroundObserver
import jinproject.aideo.design.R
import jinproject.aideo.gallery.TranscribeService
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class AideoApplication : Application() {
    @Inject lateinit var foregroundObserver: ForegroundObserver

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

        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)
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