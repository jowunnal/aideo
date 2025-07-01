package jinproject.aideo.player

import android.content.ContentUris
import androidx.compose.runtime.Stable
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import jinproject.aideo.core.MediaFileManager
import jinproject.aideo.core.toOriginUri
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import jinproject.aideo.data.repository.GalleryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val localPlayerDataSource: LocalPlayerDataSource,
    private val mediaFileManager: MediaFileManager,
    private val galleryRepository: GalleryRepository,
    private val exoPlayerManager: ExoPlayerManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val currentVideoUri: String =
        savedStateHandle.toRoute<PlayerRoute.Player>().videoUri.toOriginUri()

    private val languageMenuState = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PlayerUiState> =
        localPlayerDataSource.getLanguageSetting().onEach { language ->
            val id = ContentUris.parseId(currentVideoUri.toUri())

            val subtitleExist = mediaFileManager.checkSubtitleFileExist(
                id = id,
                languageCode = language
            )

            if (subtitleExist != 1) {
                galleryRepository.translateSubtitle(
                    MediaFileManager.getSubtitleFilePath(
                        id = id,
                        languageCode = language,
                    )
                )
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

    fun updateLanguageMenuShow(boolean: Boolean) {
        languageMenuState.update { boolean }
    }

    fun prepareExoplayer(languageCode: String,) {
        exoPlayerManager.prepare(
            videoUri = currentVideoUri,
            languageCode = languageCode,
        )
    }

    fun releaseExoPlayer() {
        exoPlayerManager.release()
    }

    fun seekTo(pos: Long) {
        exoPlayerManager.seekTo(pos)
    }

    fun getExoPlayer(): Player = exoPlayerManager.getExoPlayer()
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