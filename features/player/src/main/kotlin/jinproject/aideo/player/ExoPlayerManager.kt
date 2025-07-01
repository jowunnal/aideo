package jinproject.aideo.player

import android.content.ContentUris
import android.content.Context
import androidx.compose.runtime.Stable
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Player.STATE_READY
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.MediaFileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject

@Module
@InstallIn(ViewModelComponent::class)
object ExoPlayerManagerModule {
    @Provides
    fun provideExoPlayerManager(@ApplicationContext context: Context): ExoPlayerManager =
        ExoPlayerManager(context)
}

@Stable
class ExoPlayerManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setupPlayerListener()
    }

    val playingState: StateFlow<PlayingState> field = MutableStateFlow(
        PlayingState(
            isReady = false,
            currentPosition = 0L,
            duration = 0L
        )
    )

    private fun ExoPlayer.setupPlayerListener() {
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    STATE_READY -> {
                        playingState.value = playingState.value.copy(
                            duration = exoPlayer.duration,
                            isReady = true
                        )
                    }

                    STATE_BUFFERING -> {
                        playingState.value = playingState.value.copy(isReady = false)
                    }

                    STATE_ENDED -> {

                    }

                    STATE_IDLE -> {

                    }
                }
            }
        })
    }

    fun getExoPlayer(): ExoPlayer = exoPlayer

    fun prepare(videoUri: String, languageCode: String) {
        val subTitleConfiguration = MediaItem.SubtitleConfiguration.Builder(
            File(
                context.filesDir,
                MediaFileManager.getSubtitleFilePath(
                    id = ContentUris.parseId(videoUri.toUri()),
                    languageCode = languageCode,
                )
            ).toUri()
        )
            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(videoUri.toUri())
            .setSubtitleConfigurations(listOf(subTitleConfiguration))
            .build()

        with(exoPlayer) {
            setMediaItem(mediaItem)
            prepare()
        }

        if (playingState.value.currentPosition > 0L)
            seekTo(playingState.value.currentPosition)
    }

    fun seekTo(position: Long) {
        exoPlayer.seekTo(position)
        playingState.value = playingState.value.copy(currentPosition = position)
    }

    fun release() {
        exoPlayer.release()
    }

    fun isPlaying(): Boolean = exoPlayer.isPlaying

    fun getCurrentPosition(): Long = exoPlayer.currentPosition

    fun getDuration(): Long = exoPlayer.duration

    fun getPlaybackState(): Int = exoPlayer.playbackState
}

data class PlayingState(
    val isReady: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L
) 