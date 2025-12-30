package jinproject.aideo.gallery

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import jinproject.aideo.core.audio.AndroidMediaFileManager
import jinproject.aideo.core.audio.VideoItem
import jinproject.aideo.core.inference.senseVoice.SenseVoiceManager
import jinproject.aideo.core.inference.whisper.WhisperManager
import jinproject.aideo.core.utils.parseUri
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import jinproject.aideo.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TranscribeService : LifecycleService() {
    @Inject
    lateinit var androidMediaFileManager: AndroidMediaFileManager

    @Inject
    lateinit var senseVoiceManager: SenseVoiceManager

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
            job = lifecycleScope.launch(Dispatchers.Default) {
                senseVoiceManager.initialize()
                if (senseVoiceManager.isReady) {
                    processSubtitle(videoItem)
                    stopSelf()
                }
            }

        return START_NOT_STICKY
    }

    private suspend fun processSubtitle(videoItem: VideoItem) {
        val languageCode = localPlayerDataSource.getLanguageSetting().first()
        val isSubtitleExist = androidMediaFileManager.checkSubtitleFileExist(
            id = videoItem.id,
            languageCode = languageCode
        )

        when (isSubtitleExist) {
            1 -> {
                // 이미 자막이 존재하는 경우: 딥링크로 플레이어 이동
                launchPlayerDeepLink(videoItem)
            }

            -1 -> {
                // 자막 파일이 없으므로 음성 추출 및 자막 생성
                extractAudioAndTranscribe(videoItem = videoItem, languageCode = languageCode)
            }

            else -> {
                // 자막 파일은 있으나 번역이 필요한 경우
                translateAndNotifySuccess(videoItem)
            }
        }
    }

    private suspend fun translateAndNotifySuccess(videoItem: VideoItem) {
        try { mediaRepository.translateSubtitle(videoItem.id) }
        catch (e: Exception) {
            Log.d("test","번역 실패 : ${e.stackTraceToString()}")
        }
        Log.d("test","성공")

        launchPlayerDeepLink(videoItem)

        notifyTranscriptionResult(
            title = "자막 생성 성공",
            description = "자막 생성이 성공적으로 완료되었어요.",
            videoItem = videoItem,
        )
    }

    private suspend fun extractAudioAndTranscribe(videoItem: VideoItem, languageCode: String) {
        runCatching {
            senseVoiceManager.transcribe(videoItem)
        }.onSuccess {
            notifyTranscriptionResult(
                title = "자막 생성 성공",
                description = "자막 생성이 성공적으로 완료되었어요.",
                videoItem = videoItem,
            )
            launchPlayerDeepLink(videoItem)
        }.onFailure { exception ->
            notifyTranscriptionResult(
                title = "자막 생성 실패",
                description = "자막 생성에 실패했어요. (${exception.message})",
                videoItem = null,
            )
        }
    }

    private fun launchPlayerDeepLink(videoItem: VideoItem) {
        if (!((application as ForegroundObserver).isForeground))
            return

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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSmallIcon(jinproject.aideo.design.R.mipmap.ic_aideo_app)
            .setContentTitle(title)
            .setContentText(description)

        videoItem?.let {
            val deepLinkIntent = Intent(
                Intent.ACTION_VIEW,
                "aideo://app/player/${videoItem.uri.parseUri()}".toUri(),
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
        senseVoiceManager.release()
        job?.cancel()
        job = null
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 999
        const val NOTIFICATION_CHANNEL_ID = "TranscribeVideo"
    }
}

interface ForegroundObserver : LifecycleEventObserver {
    var isForeground: Boolean

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_RESUME)
            isForeground = true
        else if (event == Lifecycle.Event.ON_STOP)
            isForeground = false
    }
}