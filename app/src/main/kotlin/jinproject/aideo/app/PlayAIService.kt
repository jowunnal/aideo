package jinproject.aideo.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.play.core.aipacks.AiPackStateUpdateListener
import com.google.android.play.core.aipacks.model.AiPackStatus
import dagger.hilt.android.AndroidEntryPoint
import jinproject.aideo.core.utils.getAiPackManager
import jinproject.aideo.gallery.TranscribeService
import jinproject.aideo.gallery.TranscribeService.Companion.NOTIFICATION_TRANSCRIBE_ID

@AndroidEntryPoint
class PlayAIService : Service() {
    private lateinit var aiPackListener: AiPackStateUpdateListener
    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }
    private lateinit var notification: NotificationCompat.Builder

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        val exitIntent = Intent(this, TranscribeService::class.java).apply {
            putExtra("status", true)
        }

        val exitPendingIntent = PendingIntent.getService(
            this,
            556,
            exitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(jinproject.aideo.design.R.mipmap.ic_aideo_app)
            .addAction(
                jinproject.aideo.design.R.drawable.ic_x,
                getString(jinproject.aideo.design.R.string.notification_action_stop),
                exitPendingIntent
            )

        aiPackListener = AiPackStateUpdateListener { state ->
            if (state.status() == AiPackStatus.DOWNLOADING) {
                val progress =
                    (state.bytesDownloaded() / state.totalBytesToDownload()).toInt() * 100
                notification
                    .setContentTitle(getString(jinproject.aideo.design.R.string.download_ai_pack_in_progress, state.name()))
                    .setProgress(100, progress, false)
                notificationManager.notify(DOWNLOAD_AI_PACK_NOTIFICATION, notification.build())
            } else if (state.status() == AiPackStatus.COMPLETED) {
                stopSelf()
            }
        }
        getAiPackManager().registerListener(aiPackListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                startForeground(
                    NOTIFICATION_TRANSCRIBE_ID,
                    notification.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            else
                startForeground(NOTIFICATION_TRANSCRIBE_ID, notification.build())
        }

        val offFlag = intent?.getBooleanExtra("status", false)

        if (offFlag != null && offFlag) {
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        if (::aiPackListener.isInitialized)
            getAiPackManager().unregisterListener(aiPackListener)
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "Download Ai Pack Channel"
        const val DOWNLOAD_AI_PACK_NOTIFICATION = 555
    }
}