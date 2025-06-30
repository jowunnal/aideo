package jinproject.aideo.gallery

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import jinproject.aideo.core.WhisperManager
import jinproject.aideo.core.parseUri
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import jinproject.aideo.data.datasource.remote.RemoteGCPDataSource
import jinproject.aideo.data.datasource.remote.model.request.DetectLanguageRequest
import jinproject.aideo.data.repository.GalleryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

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

    @Inject
    lateinit var galleryRepository: GalleryRepository

    private var job: Job? = null
    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    override fun onCreate() {
        super.onCreate()

        lifecycleScope.launch {
            whisperManager.load()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)

        if (intent != null) {
            startForeground()
        }

        val videoItem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent?.getParcelableExtra("videoItem", VideoItem::class.java)
        else
            intent?.getParcelableExtra<VideoItem>("videoItem")

        val offFlag = intent?.getBooleanExtra("status", false)

        offFlag?.let {
            if (it) {
                job?.cancel()
                stopSelf()
            }
        }

        if (videoItem != null && job == null && whisperManager.isReady)
            job = lifecycleScope.launch {
                processSubtitle(videoItem)
                stopSelf()
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
                translateAndNotifySuccess(videoItem, languageCode)
            }

            -1 -> {
                // 자막 파일이 없으므로 음성 추출 및 자막 생성
                extractAudioAndTranscribe(videoItem, languageCode)
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

    private suspend fun translateAndNotifySuccess(videoItem: VideoItem, languageCode: String) {
        galleryRepository.translateSubtitle(
            mediaFileManager.getSubtitleFilePath(
                id = videoItem.id,
                languageCode = languageCode,
            )
        )
        notifyTranscriptionResult(
            title = "자막 생성 성공",
            description = "자막 생성이 성공적으로 완료되었어요.",
            videoItem = videoItem,
        )
    }

    private suspend fun extractAudioAndTranscribe(videoItem: VideoItem, languageCode: String) {
        val audioResult = mediaFileManager.extractAudioFromVideo(
            videoUri = videoItem.uri.toUri(),
            outputFileName = videoItem.id.toString(),
        )

        audioResult.onSuccess { audioFileAbsolutePath ->
            whisperManager.transcribeAudio(
                audioFileAbsolutePath = audioFileAbsolutePath,
                getLanguage = { content ->
                    remoteGCPDataSource.detectLanguage(
                        DetectLanguageRequest(content = content)
                    ).languages.first().languageCode
                }
            )
            translateAndNotifySuccess(videoItem, languageCode)
        }.onFailure { exception ->
            notifyTranscriptionResult(
                title = "자막 생성 실패",
                description = "자막 생성에 실패했어요. (${exception.message})",
                videoItem = null,
            )
        }
    }

    private fun launchPlayerDeepLink(videoItem: VideoItem) {
        val deepLinkUri = "aideo://player/player?videoUri=${videoItem.uri.parseUri()}".toUri()
        val deepLinkIntent = Intent(
            Intent.ACTION_VIEW,
            deepLinkUri,
            this@TranscribeService,
            Class.forName("jinproject.aideo.app.MainActivity")
        )

        TaskStackBuilder.create(this@TranscribeService).run {
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

        NotificationCompat.Builder(applicationContext, "Transcribe Video")
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
        val notification = NotificationCompat.Builder(applicationContext, "Transcribe Video")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(jinproject.aideo.design.R.mipmap.ic_aideo_app)
            .setContentTitle(title)
            .setContentText(description)

        videoItem?.let {
            val deepLinkIntent = Intent(
                Intent.ACTION_VIEW,
                "aideo://player/player?videoUri=${videoItem.uri.parseUri()}".toUri(),
                this,
                Class.forName("jinproject.aideo.app.MainActivity")
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
    }
}