package jinproject.aideo.gallery

import android.app.Notification
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
import jinproject.aideo.core.SpeechToTranscription
import jinproject.aideo.core.TranslationManager
import jinproject.aideo.core.media.VideoItem
import jinproject.aideo.core.utils.parseUri
import jinproject.aideo.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class TranscribeService : LifecycleService() {
    @Inject
    lateinit var mediaRepository: MediaRepository

    @Inject
    lateinit var speechToTranscription: SpeechToTranscription

    @Inject
    lateinit var translationManager: TranslationManager

    private var job: Job? = null
    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    override fun onCreate() {
        super.onCreate()

        speechToTranscription.inferenceProgress.onEach { p ->
            if (p == 1f)
                notifyTranscribe(
                    contentTitle = getString(jinproject.aideo.design.R.string.notification_starting_subtitle_translation),
                    progress = null
                )
            else
                notifyTranscribe(
                    contentTitle = getString(jinproject.aideo.design.R.string.notification_creating_subtitles),
                    progress = p
                )
        }.launchIn(lifecycleScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent != null) {
            notifyTranscribe(
                contentTitle = getString(jinproject.aideo.design.R.string.notification_starting_subtitle_creation),
                progress = null
            ).also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    startForeground(
                        NOTIFICATION_TRANSCRIBE_ID,
                        it,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                else
                    startForeground(NOTIFICATION_TRANSCRIBE_ID, it)
            }
        }

        val videoItem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent?.getParcelableExtra("videoItem", VideoItem::class.java)
        else
            intent?.getParcelableExtra("videoItem")

        val offFlag = intent?.getBooleanExtra("status", false)

        if (offFlag != null && offFlag) {
            speechToTranscription.release()
            stopSelf()
        }

        if (videoItem != null && job == null)
            job = lifecycleScope.launch(Dispatchers.Default) {
                notificationManager.cancel(NOTIFICATION_RESULT_ID)
                speechToTranscription.initialize()
                translationManager.initialize()
                if (speechToTranscription.isReady) {
                    processSubtitle(videoItem)
                    notificationManager.cancel(NOTIFICATION_TRANSCRIBE_ID)
                    stopSelf()
                }
            }

        return START_NOT_STICKY
    }

    private suspend fun processSubtitle(videoItem: VideoItem) {
        val isSubtitleExist = mediaRepository.checkSubtitleFileExist(videoItem.id)

        when (isSubtitleExist) {
            1 -> {
                // 이미 자막이 존재하는 경우: 딥링크로 플레이어 이동
                launchPlayer(videoItem.uri)
            }

            -1 -> {
                // 자막 파일이 없으므로 음성 추출 및 자막 생성
                extractAudioAndTranscribe(videoItem = videoItem)
            }

            else -> {
                // 자막 파일은 있으나 번역이 필요한 경우
                notifyTranscribe(
                    contentTitle = getString(jinproject.aideo.design.R.string.notification_starting_subtitle_translation),
                    progress = null
                )
                translateAndNotifySuccess(videoItem)
            }
        }
    }

    private suspend fun translateAndNotifySuccess(videoItem: VideoItem) {
        translationManager.translateSubtitle(videoItem.id)

        launchPlayer(videoItem.uri)

        notifyTranscriptionResult(
            title = getString(jinproject.aideo.design.R.string.notification_subtitle_translation_completed),
            description = getString(jinproject.aideo.design.R.string.notification_subtitle_translation_completed_desc),
            videoUri = videoItem.uri,
        )
    }

    private suspend fun extractAudioAndTranscribe(videoItem: VideoItem) {
        runCatching {
            speechToTranscription.transcribe(videoItem)
            translationManager.translateSubtitle(videoItem.id)
        }.onSuccess {
            notifyTranscriptionResult(
                title = getString(jinproject.aideo.design.R.string.notification_subtitle_creation_completed),
                description = getString(jinproject.aideo.design.R.string.notification_subtitle_creation_completed_desc),
                videoUri = videoItem.uri
            )
            launchPlayer(videoItem.uri)
        }.onFailure { exception ->
            Timber.d("exception: ${exception.stackTraceToString()}")
            notifyTranscriptionResult(
                title = getString(jinproject.aideo.design.R.string.notification_subtitle_creation_failed),
                description = getString(
                    jinproject.aideo.design.R.string.notification_subtitle_creation_failed_desc,
                    exception.message ?: ""
                ),
                videoUri = null,
            )
        }
    }

    private fun launchPlayer(videoUri: String) {
        val target = Class.forName(MAIN_ACTIVITY)
        if ((applicationContext as ForegroundObserver).isForeground)
            startActivity(
                Intent(this, target).apply {
                    putExtra("videoUri", videoUri.parseUri())
                    putExtra("deepLink", false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
        else
            launchPlayerDeepLink(videoUri)
    }

    private fun launchPlayerDeepLink(videoUri: String) {
        val deepLinkUri = "aideo://app/player/${videoUri.parseUri()}".toUri()
        val deepLinkIntent = Intent(
            Intent.ACTION_VIEW,
            deepLinkUri,
            this,
            Class.forName(MAIN_ACTIVITY),
        )

        TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(deepLinkIntent)
        }.startActivities()
    }

    private fun notifyTranscribe(contentTitle: String, progress: Float?): Notification {
        val exitIntent = Intent(this, TranscribeService::class.java).apply {
            putExtra("status", true)
        }

        val exitPendingIntent = PendingIntent.getService(
            this,
            NOTIFICATION_TRANSCRIBE_ID,
            exitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val noti = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(jinproject.aideo.design.R.mipmap.ic_aideo_app)
            .setContentTitle(contentTitle)
            .addAction(
                jinproject.aideo.design.R.drawable.ic_x,
                getString(jinproject.aideo.design.R.string.notification_action_stop),
                exitPendingIntent
            )

        progress?.let {
            noti.setProgress(100, (progress * 100f).toInt(), false)
        }

        return noti.build().apply {
            notificationManager.notify(NOTIFICATION_TRANSCRIBE_ID, this)
        }
    }

    private fun notifyTranscriptionResult(
        title: String,
        description: String,
        videoUri: String?,
    ) {
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(jinproject.aideo.design.R.mipmap.ic_aideo_app)
            .setContentTitle(title)
            .setContentText(description)

        videoUri?.let {
            val deepLinkIntent = Intent(
                Intent.ACTION_VIEW,
                "aideo://app/player/${it.parseUri()}".toUri(),
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

        notificationManager.notify(NOTIFICATION_RESULT_ID, notification.build())
    }

    override fun onDestroy() {
        speechToTranscription.release()
        job?.cancel()
        job = null
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_TRANSCRIBE_ID = 999
        const val NOTIFICATION_RESULT_ID = 123
        const val NOTIFICATION_CHANNEL_ID = "TranscribeVideo"
        const val MAIN_ACTIVITY = "jinproject.aideo.app.MainActivity"
    }
}

interface ForegroundObserver : LifecycleEventObserver {
    var isForeground: Boolean

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        isForeground = source.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
}