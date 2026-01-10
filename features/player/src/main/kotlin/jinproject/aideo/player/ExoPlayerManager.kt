package jinproject.aideo.player

import android.content.Context
import android.util.Log
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
import jinproject.aideo.data.repository.impl.getSubtitleFileIdentifier
import kotlinx.coroutines.Job
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
        exoPlayer?.release()
        exoPlayer = null
    }

    suspend fun observePlayerPosition() {
        if (playerPositionObserver == null)
            playerPositionObserver = coroutineScope {
                launch {
                    while (playerState == PlayerState.Playing) {
                        getExoPlayer()?.let { player ->
                            updateCurrentPosition(player.currentPosition)
                        }

                        delay(50)
                    }
                }
            }
    }

    private fun cancelObservingPlayerPosition() {
        playerPositionObserver?.cancel()
        playerPositionObserver = null
    }

    private fun ExoPlayer.setupPlayerListener() {
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    if (playerState.value !is PlayerState.Playing)
                        playerState.value = PlayerState.Playing.getDefault()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                exoPlayer?.let { player ->
                    when (playbackState) {
                        STATE_READY -> {
                            (playerState.value as? PlayerState.Playing)?.let { state ->
                                state.apply {
                                    updateIsReady(true)
                                    updateDuration(player.duration)
                                }
                            }
                        }

                        STATE_BUFFERING -> {
                            (playerState.value as? PlayerState.Playing)?.updateIsReady(false)
                        }

                        STATE_ENDED -> {}

                        STATE_IDLE -> {
                            playerState.value = PlayerState.Idle
                            cancelObservingPlayerPosition()
                        }
                    }
                }
            }

            @OptIn(UnstableApi::class)
            override fun onCues(cueGroup: CueGroup) {
                val subtitle = cueGroup.cues.joinToString(separator = "\n") { it.text.toString() }

                Log.d("test", "subtitle : $subtitle")
                (playerState.value as? PlayerState.Playing)?.updateSubtitle(subtitle)
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
                playWhenReady = true
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

        exoPlayer?.replaceMediaItem(
            0,
            MediaItem.Builder()
                .setSubtitleConfigurations(listOf(subTitleConfiguration))
                .build()
        )
    }

    fun seekTo(pos: Long) {
        exoPlayer?.seekTo(pos)
    }

    private fun updateCurrentPosition(pos: Long) {
        (playerState.value as? PlayerState.Playing)?.updateCurrentPos(pos)
    }
}

@Stable
sealed class PlayerState {
    object Idle : PlayerState()

    class Playing : PlayerState() {
        var isReady: Boolean = false
            private set

        var currentPosition: Long by mutableLongStateOf(0L)
            private set

        var subTitle: String by mutableStateOf("")
            private set

        var duration: Long by mutableLongStateOf(0L)
            private set

        var isSubTitleAdded: Boolean = false
            private set

        fun updateIsReady(bool: Boolean) {
            isReady = bool
        }

        fun updateCurrentPos(pos: Long) {
            currentPosition = pos
        }

        fun updateSubtitle(subtitle: String) {
            subTitle = subtitle
            isSubTitleAdded = true
        }

        fun updateDuration(duration: Long) {
            this.duration = duration
        }

        companion object {
            fun getDefault(): Playing = Playing()
        }
    }
}