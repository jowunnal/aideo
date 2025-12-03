package jinproject.aideo.player

import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import jinproject.aideo.core.audio.MediaFileManagerImpl
import jinproject.aideo.core.utils.toOriginUri
import jinproject.aideo.core.utils.toVideoItemId
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import jinproject.aideo.data.repository.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val localPlayerDataSource: LocalPlayerDataSource,
    private val mediaFileManagerImpl: MediaFileManagerImpl,
    private val mediaRepository: MediaRepository,
    private val exoPlayerManager: ExoPlayerManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val currentVideoUri: String =
        savedStateHandle.toRoute<PlayerRoute.Player>().videoUri.toOriginUri()

    private var job: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PlayerUiState> =
        localPlayerDataSource.getLanguageSetting().onEach { language ->
            val id = currentVideoUri.toVideoItemId()

            val subtitleExist = mediaFileManagerImpl.checkSubtitleFileExist(
                id = id,
                languageCode = language
            )

            if (subtitleExist != 1) {
                mediaRepository.translateSubtitle(id)
            }

            prepareExoplayer(language)
        }.flatMapLatest { language ->
            exoPlayerManager.playingState.map { playingState ->
                PlayerUiState(
                    currentLanguage = language,
                    playingState = playingState,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = PlayerUiState(
                currentLanguage = Locale.getDefault().language,
                playingState = PlayingState()
            )
        )

    fun updateLanguage(languageCode: LanguageCode) {
        viewModelScope.launch {
            localPlayerDataSource.setLanguageSetting(languageCode.code)
        }
    }

    fun prepareExoplayer(languageCode: String) {
        exoPlayerManager.prepare(
            videoUri = currentVideoUri,
            languageCode = languageCode,
        )
        observePlayerPosition()
    }

    fun releaseExoPlayer() {
        exoPlayerManager.release()
        cancelObservingPlayerPosition()
    }

    fun seekTo(pos: Long) {
        exoPlayerManager.seekTo(pos)
    }

    fun getExoPlayer(): Player = exoPlayerManager.getExoPlayer()

    private fun observePlayerPosition() {
        if (job == null)
            job = viewModelScope.launch {
                uiState.collectLatest { uiState ->
                    if (uiState.playingState.isPlaying) {
                        with(getExoPlayer()) {
                            while (uiState.playingState.isPlaying) {
                                exoPlayerManager.updateCurrentPosition(currentPosition)
                                delay(100)
                            }
                        }
                    }
                }
            }
    }

    private fun cancelObservingPlayerPosition() {
        job?.cancel()
        job = null
    }
}

@Stable
enum class LanguageCode(val code: String) {
    KO("ko"),
    EN("en"),
    JA("ja"),
    ZH("zh");
}

@Stable
data class PlayerUiState(
    val playingState: PlayingState,
    val currentLanguage: String,
)