package jinproject.aideo.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Player.STATE_READY
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.utils.toVideoItemId
import jinproject.aideo.data.TranslationManager.getSubtitleFileIdentifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
@Stable
class ExoPlayerManager @Inject constructor(@ApplicationContext private val context: Context) {

    private var exoPlayer: ExoPlayer? = null

    val playerState: StateFlow<PlayerState> field: MutableStateFlow<PlayerState> = MutableStateFlow(
        PlayerState.Idle
    )

    private var playerPositionObserver: Job? = null
    private val resumeSignal = Channel<Unit>(capacity = 1)

    @OptIn(UnstableApi::class)
    fun initialize() {
        if (exoPlayer == null) {
            exoPlayer =
                ExoPlayer.Builder(context)
                    .build().apply {
                        setSeekBackIncrementMs(5000)
                        setupPlayerListener()
                    }
        }
    }

    fun release() {
        cancelObservingPlayerPosition()
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
    }

    suspend fun observePlayerPosition() {
        if (playerPositionObserver == null)
            playerPositionObserver = coroutineScope {
                launch {
                    for (i in resumeSignal) {
                        while ((playerState.value as? PlayerState.Ready)?.isPlaying ?: false) {
                            getExoPlayer()?.let { player ->
                                updateCurrentPosition(player.currentPosition)
                            }

                            delay(100)
                        }
                    }
                }
            }
    }

    private fun cancelObservingPlayerPosition() {
        resumeSignal.cancel()
        playerPositionObserver?.cancel()
        playerPositionObserver = null
    }

    private fun ExoPlayer.setupPlayerListener() {
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                (playerState.value as? PlayerState.Ready)?.updateIsPlaying(isPlaying)

                if (isPlaying) {
                    resumeSignal.trySend(Unit)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                exoPlayer?.let { player ->
                    when (playbackState) {
                        STATE_READY -> {
                            when (playerState.value) {
                                PlayerState.Idle -> {
                                    playerState.value = PlayerState.Ready.getDefault().apply {
                                        updateDuration(player.duration)
                                    }
                                }

                                is PlayerState.Ready -> {
                                    (playerState.value as PlayerState.Ready).apply {
                                        updateDuration(player.duration)
                                    }
                                }
                            }
                        }

                        STATE_BUFFERING -> {}

                        STATE_ENDED -> {}

                        STATE_IDLE -> {
                            playerState.value = PlayerState.Idle
                        }
                    }
                }
            }

            @OptIn(UnstableApi::class)
            override fun onCues(cueGroup: CueGroup) {
                val subtitle = cueGroup.cues.joinToString(separator = "\n") { it.text.toString() }

                (playerState.value as? PlayerState.Ready)?.updateSubtitle(subtitle)
            }
        })
    }

    fun getExoPlayer(): ExoPlayer? = exoPlayer

    fun prepare(videoUri: String, languageCode: String) {
        val subTitleConfiguration = MediaItem.SubtitleConfiguration.Builder(
            File(
                context.filesDir,
                getSubtitleFileIdentifier(
                    id = videoUri.toVideoItemId(),
                    languageCode = languageCode,
                )
            ).toUri()
        )
            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setLanguage(languageCode)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(videoUri.toUri())
            .setSubtitleConfigurations(listOf(subTitleConfiguration))
            .build()

        exoPlayer?.let { player ->
            player.apply {
                setMediaItem(mediaItem)
                prepare()
            }
        }
    }

    fun replaceSubtitle(videoUri: String, languageCode: String) {
        val subTitleConfiguration = MediaItem.SubtitleConfiguration.Builder(
            File(
                context.filesDir,
                getSubtitleFileIdentifier(
                    id = videoUri.toVideoItemId(),
                    languageCode = languageCode,
                )
            ).toUri()
        )
            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setLanguage(languageCode)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        exoPlayer?.let { player ->
            val mediaItem = MediaItem.Builder()
                .setUri(videoUri.toUri())
                .setSubtitleConfigurations(listOf(subTitleConfiguration))
                .build()

            player.apply {
                val currentIdx = currentMediaItemIndex
                val pos = currentPosition

                stop()
                removeMediaItem(currentIdx)
                setMediaItem(mediaItem)
                prepare()
                seekTo(pos)
                playWhenReady = true
            }
        }
    }

    fun seekTo(pos: Long) {
        exoPlayer?.seekTo(pos)
    }

    private fun updateCurrentPosition(pos: Long) {
        (playerState.value as? PlayerState.Ready)?.updateCurrentPos(pos)
    }
}

@Stable
sealed class PlayerState {
    object Idle : PlayerState()

    class Ready : PlayerState() {
        var isPlaying: Boolean = false
            private set

        var currentPosition: Long by mutableLongStateOf(0L)
            private set

        var subTitle: String by mutableStateOf("")
            private set

        var duration: Long by mutableLongStateOf(0L)
            private set

        var isSubTitleAdded: Boolean = false
            private set

        fun updateIsPlaying(bool: Boolean) {
            isPlaying = bool
        }

        fun updateCurrentPos(pos: Long) {
            currentPosition = pos
        }

        fun updateSubtitle(subtitle: String) {
            subTitle = subtitle
            isSubTitleAdded = true
        }

        fun updateDuration(duration: Long) {
            this.duration = duration.coerceAtLeast(0L)
        }

        companion object {
            fun getDefault(): Ready = Ready()
        }
    }
}