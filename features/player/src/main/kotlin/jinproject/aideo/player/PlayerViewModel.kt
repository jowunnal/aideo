package jinproject.aideo.player

import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import jinproject.aideo.core.media.AndroidMediaFileManager
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.core.utils.toOriginUri
import jinproject.aideo.core.utils.toVideoItemId
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import jinproject.aideo.data.repository.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val androidMediaFileManager: AndroidMediaFileManager,
    private val mediaRepository: MediaRepository,
    private val exoPlayerManager: ExoPlayerManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    init {
        initExoPlayer()
    }

    private val currentVideoUri: String =
        savedStateHandle.toRoute<PlayerRoute.Player>().videoUri.toOriginUri()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PlayerUiState> =
        localPlayerDataSource.getInferenceTargetLanguage().onEach { language ->
            val id = currentVideoUri.toVideoItemId()

            val subtitleExist = androidMediaFileManager.checkSubtitleFileExist(
                id = id,
                languageCode = language
            )

            if (subtitleExist != 1) {
                mediaRepository.translateSubtitle(id)
            }

            getExoPlayer().also {
                if (exoPlayerManager.playerState.value is PlayerState.Playing && (exoPlayerManager.playerState.value as PlayerState.Playing).isSubTitleAdded) {
                    exoPlayerManager.replaceSubtitle(
                        videoUri = currentVideoUri,
                        languageCode = language
                    )
                }
            }
        }.flatMapLatest { language ->
            exoPlayerManager.playerState.map { playingState ->
                PlayerUiState(
                    currentLanguage = language,
                    playerState = playingState,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = PlayerUiState(
                currentLanguage = Locale.getDefault().language,
                playerState = PlayerState.Idle
            )
        )

    fun updateSubtitleLanguage(languageCode: LanguageCode) {
        viewModelScope.launch {
            localPlayerDataSource.setSubtitleLanguage(languageCode.code)
        }
    }

    fun prepareExoplayer(languageCode: String) {
        exoPlayerManager.prepare(
            videoUri = currentVideoUri,
            languageCode = languageCode,
        )
        viewModelScope.launch {
            exoPlayerManager.observePlayerPosition()
        }
    }

    fun seekTo(pos: Long) {
        exoPlayerManager.seekTo(pos)
    }

    fun getExoPlayer(): Player? = exoPlayerManager.getExoPlayer()

    fun initExoPlayer() {
        exoPlayerManager.initialize()
    }

    override fun onCleared() {
        exoPlayerManager.release()
        super.onCleared()
    }
}

@Stable
data class PlayerUiState(
    val playerState: PlayerState,
    val currentLanguage: String,
)