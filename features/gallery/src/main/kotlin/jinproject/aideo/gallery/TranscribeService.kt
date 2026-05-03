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
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import jinproject.aideo.core.category.stt.SpeechToTranscription
import jinproject.aideo.core.category.stt.SpeechToTranscription.UnsupportedTranscriptionLanguageException
import jinproject.aideo.core.category.translation.SubtitleTranslator
import jinproject.aideo.core.common.ForegroundObserver
import jinproject.aideo.core.inference.translation.MlKitTranslation.UnsupportedMlKitLanguageException
import jinproject.aideo.core.inference.translation.TranslationAvailableModel
import jinproject.aideo.core.media.VideoItem
import jinproject.aideo.core.utils.parseUri
import jinproject.aideo.data.SubtitleFileConfig
import jinproject.aideo.data.datasource.local.LocalSettingDataSource
import jinproject.aideo.data.repository.MediaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@AndroidEntryPoint
class TranscribeService : LifecycleService() {
    @Inject
    lateinit var mediaRepository: MediaRepository

    @Inject
    lateinit var speechToTranscription: SpeechToTranscription

    @Inject
    lateinit var translator: SubtitleTranslator

    @Inject
    lateinit var foregroundObserver: ForegroundObserver

    @Inject
    lateinit var localSettingDataSource: LocalSettingDataSource

    private var job: Job? = null
    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    @OptIn(ExperimentalAtomicApi::class)
    private var isVisible: AtomicBoolean = AtomicBoolean(true)

    @OptIn(FlowPreview::class, ExperimentalAtomicApi::class)
    override fun onCreate() {
        super.onCreate()

        speechToTranscription.progress.debounce(100).filter { it > 0f }.onEach { p ->
            if (p == 1f) {
                if (isVisible.load())
                    notifyTranscribe(
                        contentTitle = getString(jinproject.aideo.design.R.string.notification_starting_subtitle_translation),
                        progress = null
                    )
            } else
                notifyTranscribe(
                    contentTitle = getString(jinproject.aideo.design.R.string.notification_creating_subtitles),
                    progress = p
                )
        }.launchIn(lifecycleScope)

        translator.progress.debounce(100).filter { it > 0f }.onEach { p ->
            notifyTranscribe(
                contentTitle = getString(jinproject.aideo.design.R.string.notification_starting_subtitle_translation_in_progress),
                progress = p
            )
        }.launchIn(lifecycleScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val offFlag = intent?.getBooleanExtra("status", false)

        if (offFlag != null && offFlag) {
            lifecycleScope.launch {
                stopForeground(STOP_FOREGROUND_REMOVE)
                job?.cancelAndJoin()
                stopSelf()
            }
            return START_NOT_STICKY
        }

        if (intent != null)
            startForegroundNotification()

        val videoItem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("videoItem", VideoItem::class.java)
        } else {
            intent?.getParcelableExtra("videoItem")
        }

        if (videoItem != null) {
            startTranscription(
                videoUri = videoItem.uri,
                videoId = videoItem.id,
            )
        } else if (intent == null) {
            lifecycleScope.launch {
                val cachedUri = speechToTranscription.getPendingInferenceVideoUri()
                if (cachedUri.isNotEmpty()) {
                    val videoId = SubtitleFileConfig.toSubtitleFileId(cachedUri)
                    if (videoId != null) {
                        startTranscription(videoUri = cachedUri, videoId = videoId)
                    } else {
                        stopSelf()
                    }
                } else {
                    stopSelf()
                }
            }
        }

        return START_STICKY
    }

    private fun startForegroundNotification() {
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

    @OptIn(ExperimentalAtomicApi::class)
    private fun startTranscription(videoUri: String, videoId: Long) {
        val isTranscribeRunning = job != null
        val previousJob = job

        job = lifecycleScope.launch(Dispatchers.Default) {
            previousJob?.cancelAndJoin()
            notificationManager.cancel(NOTIFICATION_RESULT_ID)
            if (!isTranscribeRunning) {
                speechToTranscription.initialize()
            } else {
                speechToTranscription.cancelAndReInitialize()
            }

            processSubtitle(videoUri = videoUri, videoId = videoId)

            if (foregroundObserver.isForeground) {
                isVisible.store(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopSelf()
            }
        }
    }

    private suspend fun processSubtitle(videoUri: String, videoId: Long) {
        val isSubtitleExist = mediaRepository.checkSubtitleFileExist(videoId)

        when (isSubtitleExist) {
            MediaRepository.EXIST -> {
                // 이미 자막이 존재하는 경우: 딥링크로 플레이어 이동
                launchPlayer(videoUri)
            }

            MediaRepository.NEED_INFERENCE -> {
                // 자막 파일이 없으므로 음성 추출 및 자막 생성
                extractAudioAndTranscribe(videoUri = videoUri, videoId = videoId)
            }

            else -> {
                // 자막 파일은 있으나 번역이 필요한 경우
                notifyTranscribe(
                    contentTitle = getString(jinproject.aideo.design.R.string.notification_starting_subtitle_translation),
                    progress = null
                )
                translateAndNotifySuccess(videoUri = videoUri, videoId = videoId)
            }
        }
    }

    private suspend fun translateAndNotifySuccess(videoUri: String, videoId: Long) {
        runCatching {
            translateSubtitle(videoId)
        }.onSuccess {
            launchPlayer(videoUri)

            notifyTranscriptionResult(
                title = getString(jinproject.aideo.design.R.string.notification_subtitle_translation_completed),
                description = getString(jinproject.aideo.design.R.string.notification_subtitle_translation_completed_desc),
                videoUri = videoUri,
            )
        }.onFailure { exception ->
            Timber.d("exception: ${exception.stackTraceToString()}")

            if (exception is CancellationException)
                throw exception

            notifyTranscriptionResult(
                title = getString(jinproject.aideo.design.R.string.notification_subtitle_creation_failed),
                description = getTranscriptionFailureDescription(exception),
                videoUri = null,
            )
        }

    }

    private suspend fun extractAudioAndTranscribe(videoUri: String, videoId: Long) {
        runCatching {
            speechToTranscription.transcribe(videoUri = videoUri, videoId = videoId)
            translateSubtitle(videoId)
        }.onSuccess {
            notifyTranscriptionResult(
                title = getString(jinproject.aideo.design.R.string.notification_subtitle_creation_completed),
                description = getString(jinproject.aideo.design.R.string.notification_subtitle_creation_completed_desc),
                videoUri = videoUri
            )
            launchPlayer(videoUri)
        }.onFailure { exception ->
            Timber.d("exception: ${exception.stackTraceToString()}")

            if (exception is CancellationException)
                throw exception

            notifyTranscriptionResult(
                title = getString(jinproject.aideo.design.R.string.notification_subtitle_creation_failed),
                description = getTranscriptionFailureDescription(exception),
                videoUri = null,
            )
        }
    }

    private suspend fun translateSubtitle(videoId: Long) {
        val translationModel = localSettingDataSource.getSelectedTranslationModel().first()
        if (TranslationAvailableModel.findByName(translationModel) == TranslationAvailableModel.M2M100)
            speechToTranscription.release()

        translator.translateSubtitle(videoId)
    }

    private fun launchPlayer(videoUri: String) {
        val target = Class.forName(MAIN_ACTIVITY)
        if (foregroundObserver.isForeground)
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

    private fun getTranscriptionFailureDescription(exception: Throwable): String =
        when (exception) {
            is UnsupportedTranscriptionLanguageException -> getString(
                jinproject.aideo.design.R.string.notification_subtitle_creation_failed_unsupported_language_desc
            )

            is UnsupportedMlKitLanguageException -> getString(
                jinproject.aideo.design.R.string.notification_subtitle_creation_failed_undetectable_language_desc
            )

            else -> getString(
                jinproject.aideo.design.R.string.notification_subtitle_creation_failed_desc,
                exception.message ?: ""
            )
        }

    override fun onDestroy() {
        speechToTranscription.release()
        translator.release()
        job = null
        notificationManager.cancel(NOTIFICATION_TRANSCRIBE_ID)
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_TRANSCRIBE_ID = 999
        const val NOTIFICATION_RESULT_ID = 123
        const val NOTIFICATION_CHANNEL_ID = "TranscribeVideo"
        const val MAIN_ACTIVITY = "jinproject.aideo.app.MainActivity"
    }
}
