package jinproject.aideo.gallery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import jinproject.aideo.core.WhisperManager
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import jinproject.aideo.data.datasource.remote.RemoteGCPDataSource
import jinproject.aideo.data.datasource.remote.model.request.DetectLanguageRequest
import jinproject.aideo.gallery.TranscribeService.Companion.Companion.NOTIFICATION_ID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.jvm.java

@AndroidEntryPoint
class TranscribeService : LifecycleService() {
    @Inject
    lateinit var mediaFileManager: MediaFileManager

    @Inject
    lateinit var whisperManager: WhisperManager

    @Inject
    lateinit var remoteGCPDataSource: RemoteGCPDataSource

    @Inject
    lateinit var localPlayerDataSource: LocalPlayerDataSource

    private var job: Job? = null
    private lateinit var notification: NotificationCompat.Builder

    override fun onCreate() {
        super.onCreate()

        val exitIntent = Intent(this, TranscribeService::class.java).apply {
            putExtra("status", true)
        }
        val exitPendingIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID,
            exitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createChannel()

        notification =
            NotificationCompat.Builder(applicationContext, "Transcribe Video")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSmallIcon(jinproject.aideo.design.R.mipmap.ic_aideo_app)
                .setContentTitle("자막 생성 중")
                .addAction(
                    jinproject.aideo.design.R.drawable.ic_x,
                    "끄기",
                    exitPendingIntent
                )
    }

    fun NotificationManager.createChannel() {
        val name = "자막 생성 포그라운드 서비스 채널"
        val descriptionText = "자막 생성을 위한 채널 입니다."
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel("Transcribe Video", name, importance).apply {
            description = descriptionText
            enableVibration(true)
            setShowBadge(true)
            enableLights(true)
            lightColor = Color.BLUE
        }

        createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)

        if (intent != null && ::notification.isInitialized)
            notification.build().also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    startForeground(
                        NOTIFICATION_ID, it,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                else
                    startForeground(NOTIFICATION_ID, it)
            }

        val videoItem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent?.getParcelableExtra("videoItem", VideoItem::class.java)
        else
            intent?.getParcelableExtra<VideoItem>("videoItem")

        if(videoItem != null && job == null)
            job = lifecycleScope.launch {
                val isSubtitleExist = mediaFileManager.checkSubtitleFileExist(
                    id = videoItem.id,
                    languageCode = localPlayerDataSource.getLanguageSetting().first()
                )

                if (!isSubtitleExist) {
                    val audioFileAbsolutePath = mediaFileManager.extractAudioFromVideo(
                        videoUri = videoItem.uri.toUri(),
                        outputFileName = videoItem.id.toString(),
                    )

                    audioFileAbsolutePath.onSuccess {
                        whisperManager.transcribeAudio(
                            audioFileAbsolutePath = it,
                            getLanguage = { content ->
                                remoteGCPDataSource.detectLanguage(
                                    DetectLanguageRequest(
                                        content = content,
                                    )
                                ).languages.first().languageCode
                            }
                        )
                    }.onFailure {
                        //TODO 비디오에서 음성 추출 실패시 예외 처리
                    }
                }

                startActivity(
                    Intent(
                        this@TranscribeService,
                        Class.forName("jinproject.aideo.app.MainActivity")
                    ).apply {
                        putExtra(TRANSCRIBE_RESULT_KEY, true)
                    })

                stopSelf()
            }
    }

    companion object {
        const val TRANSCRIBE_RESULT_KEY = "TRANSCRIBE_RESULT"
        const val NOTIFICATION_ID = 999
    }
}