package jinproject.aideo.gallery

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import jinproject.aideo.core.MediaFileManager
import jinproject.aideo.core.VideoItem
import jinproject.aideo.core.WhisperManager
import jinproject.aideo.core.parseUri
import jinproject.aideo.core.toAudioFileWAVIdentifier
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import jinproject.aideo.data.repository.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class TranscribeService : LifecycleService() {
    @Inject
    lateinit var mediaFileManager: MediaFileManager

    @Inject
    lateinit var whisperManager: WhisperManager

    @Inject
    lateinit var localPlayerDataSource: LocalPlayerDataSource

    @Inject
    lateinit var mediaRepository: MediaRepository

    private var job: Job? = null
    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent != null) {
            startForeground()
        }

        val videoItem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent?.getParcelableExtra("videoItem", VideoItem::class.java)
        else
            intent?.getParcelableExtra<VideoItem>("videoItem")

        val offFlag = intent?.getBooleanExtra("status", false)

        if (offFlag != null && offFlag) {
            job?.cancel()
            stopSelf()
        }

        if (videoItem != null && job == null)
            job = lifecycleScope.launch {
                whisperManager.load()
                if (whisperManager.isReady) {
                    processSubtitle(videoItem)
                    stopSelf()
                }
            }

        return START_NOT_STICKY
    }

    private suspend fun processSubtitle(videoItem: VideoItem) {
        val languageCode = localPlayerDataSource.getLanguageSetting().first()
        val isSubtitleExist = mediaFileManager.checkSubtitleFileExist(
            id = videoItem.id,
            languageCode = languageCode
        )

        when (isSubtitleExist) {
            1 -> {
                // 이미 자막이 존재하는 경우: 딥링크로 플레이어 이동
                launchPlayerDeepLink(videoItem)
            }

            0 -> {
                // 자막 파일은 있으나 번역이 필요한 경우
                translateAndNotifySuccess(videoItem)
            }

            -1 -> {
                // 자막 파일이 없으므로 음성 추출 및 자막 생성
                extractAudioAndTranscribe(videoItem)
            }

            else -> {
                // 예외 상황 처리
                notifyTranscriptionResult(
                    title = "자막 상태 확인 실패",
                    description = "자막 파일 상태를 확인할 수 없습니다.",
                    videoItem = null,
                )
            }
        }
    }

    private suspend fun translateAndNotifySuccess(videoItem: VideoItem) {
        mediaRepository.translateSubtitle(
            id = videoItem.id,
            languageCode = Locale.US.language,
        )

        if((application as ForegroundObserver).isForeground)
            launchPlayerDeepLink(videoItem)

        notifyTranscriptionResult(
            title = "자막 생성 성공",
            description = "자막 생성이 성공적으로 완료되었어요.",
            videoItem = videoItem,
        )
    }

    private suspend fun extractAudioAndTranscribe(videoItem: VideoItem) {
        runCatching {
            mediaFileManager.extractAudioToWavWithResample(
                videoFileAbsolutePath = videoItem.uri,
                wavIdentifier = toAudioFileWAVIdentifier(videoItem.id)
            )
        }.onSuccess {
            whisperManager.transcribeAudio(audioFileId = videoItem.id)
            translateAndNotifySuccess(videoItem = videoItem)
        }.onFailure { exception ->
            notifyTranscriptionResult(
                title = "자막 생성 실패",
                description = "자막 생성에 실패했어요. (${exception.message})",
                videoItem = null,
            )
        }
    }

    private fun launchPlayerDeepLink(videoItem: VideoItem) {
        val deepLinkUri = "aideo://app/player/${videoItem.uri.parseUri()}".toUri()
        val deepLinkIntent = Intent(
            Intent.ACTION_VIEW,
            deepLinkUri,
            this,
            Class.forName("jinproject.aideo.app.MainActivity"),
        )

        TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(deepLinkIntent)
        }.startActivities()
    }

    private fun startForeground() {
        val exitIntent = Intent(this, TranscribeService::class.java).apply {
            putExtra("status", true)
        }

        val exitPendingIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID,
            exitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(jinproject.aideo.design.R.mipmap.ic_aideo_app)
            .setContentTitle("자막 생성 중")
            .addAction(
                jinproject.aideo.design.R.drawable.ic_x,
                "끄기",
                exitPendingIntent
            ).build().also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    startForeground(
                        NOTIFICATION_ID, it,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                else
                    startForeground(NOTIFICATION_ID, it)
            }
    }

    private fun notifyTranscriptionResult(
        title: String,
        description: String,
        videoItem: VideoItem?,
    ) {
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(jinproject.aideo.design.R.mipmap.ic_aideo_app)
            .setContentTitle(title)
            .setContentText(description)

        videoItem?.let {
            val deepLinkIntent = Intent(
                Intent.ACTION_VIEW,
                "aideo://app/player/${videoItem.uri.parseUri()}".toUri()
            )

            val deepLinkPendingIntent: PendingIntent? = TaskStackBuilder.create(this).run {
                addNextIntentWithParentStack(deepLinkIntent)
                getPendingIntent(
                    0,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            notification
                .setContentIntent(deepLinkPendingIntent)
        }

        notificationManager.notify(123, notification.build())
    }

    override fun onDestroy() {
        runBlocking {
            whisperManager.release()
            job?.cancel()
            job = null
        }
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 999
        const val NOTIFICATION_CHANNEL_ID = "TranscribeVideo"
    }
}

interface ForegroundObserver: LifecycleEventObserver {
    var isForeground: Boolean

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if(event == Lifecycle.Event.ON_RESUME)
            isForeground = true
        else if(event == Lifecycle.Event.ON_STOP)
            isForeground = false
    }
}