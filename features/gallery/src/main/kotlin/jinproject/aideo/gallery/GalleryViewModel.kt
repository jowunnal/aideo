package jinproject.aideo.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jinproject.aideo.core.MediaFileManager
import jinproject.aideo.core.RestartableStateFlow
import jinproject.aideo.core.VideoItem
import jinproject.aideo.core.restartableStateIn
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import jinproject.aideo.design.component.layout.DownLoadedUiState
import jinproject.aideo.design.component.layout.DownloadableUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val mediaFileManager: MediaFileManager,
    private val localPlayerDataSource: LocalPlayerDataSource,
) : ViewModel() {

    private val videoList: MutableList<VideoItem> = mutableListOf()

    val uiState: RestartableStateFlow<DownloadableUiState> = flow {
        emit(
            GalleryUiState(
                data = videoList.toImmutableList(),
                languageCode = localPlayerDataSource.getLanguageSetting().first()
            )
        )
    }.restartableStateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DownloadableUiState.Loading,
    )

    private fun retryGetUiState() {
        uiState.restart()
    }

    fun updateVideoList(videoUris: List<String>) {
        viewModelScope.launch {
            val videoItems = videoUris.map {
                async { mediaFileManager.getVideoInfoList(it) }
            }.awaitAll().filterNotNull()

            videoList.clear()
            videoList.addAll(videoItems)
            retryGetUiState()
        }
    }
}

data class GalleryUiState(
    override val data: ImmutableList<VideoItem>,
    val languageCode: String,
) : DownLoadedUiState<ImmutableList<VideoItem>>()