package jinproject.aideo.player

import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import jinproject.aideo.core.TranslationManager
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.core.utils.toOriginUri
import jinproject.aideo.core.utils.toVideoItemId
import jinproject.aideo.data.datasource.local.LocalSettingDataSource
import jinproject.aideo.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val localSettingDataSource: LocalSettingDataSource,
    private val mediaRepository: MediaRepository,
    private val exoPlayerManager: ExoPlayerManager,
    private val savedStateHandle: SavedStateHandle,
    private val translationManager: TranslationManager,
) : ViewModel() {

    init {
        initExoPlayer()
    }

    private val currentVideoUri: String
        get() =
            savedStateHandle.toRoute<PlayerRoute.Player>().videoUri.toOriginUri()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PlayerUiState> =
        localSettingDataSource.getSubtitleLanguage().onEach { language ->
            val id = currentVideoUri.toVideoItemId()

            val subtitleExist = mediaRepository.checkSubtitleFileExist(currentVideoUri.toVideoItemId()) == 1

            if (!subtitleExist) {
                translationManager.translateSubtitle(id)
            }

            getExoPlayer().also {
                if (exoPlayerManager.playerState.value is PlayerState.Ready && (exoPlayerManager.playerState.value as PlayerState.Ready).isSubTitleAdded) {
                    exoPlayerManager.replaceSubtitle(
                        videoUri = currentVideoUri,
                        languageCode = language
                    )
                }
            }
        }.flatMapLatest { language ->
            exoPlayerManager.playerState.map { playerState ->
                PlayerUiState(
                    currentLanguage = language,
                    playerState = playerState,
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
            localSettingDataSource.setSubtitleLanguage(languageCode.code)
        }
    }

    suspend fun isSubtitleExist(language: String): Boolean {
        return mediaRepository.checkSubtitleFileExist(id = currentVideoUri.toVideoItemId(), srcLang = language) == 1
    }

    fun prepareExoplayer() {
        exoPlayerManager.prepare(
            videoUri = currentVideoUri,
            languageCode = uiState.value.currentLanguage,
        )
        viewModelScope.launch(Dispatchers.Main) {
            exoPlayerManager.observePlayerPosition()
        }
    }

    fun seekTo(pos: Long) {
        exoPlayerManager.seekTo(pos)
    }

    fun getExoPlayer(): ExoPlayer? = exoPlayerManager.getExoPlayer()

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