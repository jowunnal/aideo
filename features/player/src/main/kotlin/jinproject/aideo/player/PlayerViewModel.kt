package jinproject.aideo.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jinproject.aideo.data.datasource.local.model.VideoInfo
import jinproject.aideo.data.repository.AideoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: AideoRepository
) : ViewModel() {

    private var currentVideoUri: String = ""
    private var currentVideoIndex: Int = 0

    // 모든 비디오 목록을 가져오는 Flow
    private val _allVideosFlow = repository.getAllVideos()
        .catch { error ->
            emit(emptyList())
        }

    // 현재 언어 설정을 가져오는 Flow
    private val _currentLanguageFlow = flow {
        emit(repository.getCurrentLanguage())
    }.catch {
        emit("ko") // 기본값
    }

    // 언어 메뉴 상태
    private val _languageMenuStateFlow = MutableStateFlow(false)

    // 단일 상태로 결합 (ExoPlayer 상태는 외부에서 주입받음)
    val uiState: StateFlow<PlayerUiState> = combine(
        _allVideosFlow,
        _currentLanguageFlow,
        _languageMenuStateFlow
    ) { allVideos, currentLanguage, showLanguageMenu ->
        PlayerUiState(
            allVideos = allVideos,
            currentLanguage = currentLanguage,
            showLanguageMenu = showLanguageMenu
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = PlayerUiState()
    )

    fun initializePlayer(videoUri: String, onPlayerInitialized: () -> Unit = {}) {
        currentVideoUri = videoUri
        val currentVideos = uiState.value.allVideos
        currentVideoIndex = currentVideos.indexOfFirst { it.uri == videoUri }
        
        // 자막 로드
        loadSubtitle(videoUri)
        
        // 플레이어 초기화 콜백 호출
        onPlayerInitialized()
    }

    private fun loadSubtitle(videoUri: String) {
        viewModelScope.launch {
            val currentLanguage = repository.getCurrentLanguage()
            val subtitle = repository.getSubtitle(videoUri, currentLanguage)
            
            subtitle?.let { sub ->
                // ExoPlayer에 자막 추가
                // 실제 구현에서는 SubtitleTrack을 생성하여 추가
            }
        }
    }

    fun togglePlayPause(onToggle: () -> Unit) {
        onToggle()
    }

    fun seekTo(position: Long, onSeek: (Long) -> Unit) {
        onSeek(position)
    }

    fun previousVideo(onVideoChange: (String) -> Unit) {
        val currentVideos = uiState.value.allVideos
        if (currentVideos.isNotEmpty() && currentVideoIndex > 0) {
            currentVideoIndex--
            val previousVideo = currentVideos[currentVideoIndex]
            currentVideoUri = previousVideo.uri
            loadSubtitle(previousVideo.uri)
            onVideoChange(previousVideo.uri)
        }
    }

    fun nextVideo(onVideoChange: (String) -> Unit) {
        val currentVideos = uiState.value.allVideos
        if (currentVideos.isNotEmpty() && currentVideoIndex < currentVideos.size - 1) {
            currentVideoIndex++
            val nextVideo = currentVideos[currentVideoIndex]
            currentVideoUri = nextVideo.uri
            loadSubtitle(nextVideo.uri)
            onVideoChange(nextVideo.uri)
        }
    }

    fun setLanguage(languageCode: String) {
        viewModelScope.launch {
            repository.setLanguage(languageCode)
            loadSubtitle(currentVideoUri)
        }
    }

    fun showLanguageMenu() {
        _languageMenuStateFlow.value = true
    }

    fun hideLanguageMenu() {
        _languageMenuStateFlow.value = false
    }
}

data class PlayerUiState(
    val allVideos: List<VideoInfo> = emptyList(),
    val currentLanguage: String = "ko",
    val showLanguageMenu: Boolean = false
) 