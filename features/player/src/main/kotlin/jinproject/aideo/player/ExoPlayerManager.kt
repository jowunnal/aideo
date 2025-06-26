package jinproject.aideo.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExoPlayerManager(context: Context) {
    
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()
    
    private val _playerStateFlow = MutableStateFlow(
        PlayerState(
            isPlaying = false,
            isReady = false,
            currentPosition = 0L,
            duration = 0L
        )
    )
    
    val playerState: StateFlow<PlayerState> = _playerStateFlow.asStateFlow()
    
    init {
        setupPlayerListener()
    }
    
    private fun setupPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerStateFlow.value = _playerStateFlow.value.copy(isPlaying = isPlaying)
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        _playerStateFlow.value = _playerStateFlow.value.copy(
                            duration = exoPlayer.duration,
                            isReady = true
                        )
                    }
                    Player.STATE_BUFFERING -> {
                        _playerStateFlow.value = _playerStateFlow.value.copy(isReady = false)
                    }
                }
            }
        })
    }
    
    fun getExoPlayer(): ExoPlayer = exoPlayer
    
    fun initializePlayer(videoUri: String) {
        val mediaItem = MediaItem.fromUri(Uri.parse(videoUri))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }
    
    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }
    
    fun seekTo(position: Long) {
        exoPlayer.seekTo(position)
        _playerStateFlow.value = _playerStateFlow.value.copy(currentPosition = position)
    }
    
    fun release() {
        exoPlayer.release()
    }
    
    fun isPlaying(): Boolean = exoPlayer.isPlaying
    
    fun getCurrentPosition(): Long = exoPlayer.currentPosition
    
    fun getDuration(): Long = exoPlayer.duration
    
    fun getPlaybackState(): Int = exoPlayer.playbackState
}

data class PlayerState(
    val isPlaying: Boolean = false,
    val isReady: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L
) 