package jinproject.aideo.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jinproject.aideo.core.RestartableStateFlow
import jinproject.aideo.core.restartableStateIn
import jinproject.aideo.data.repository.GalleryRepository
import jinproject.aideo.design.component.layout.DownLoadedUiState
import jinproject.aideo.design.component.layout.DownloadableUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val galleryRepository: GalleryRepository,
    private val videoFileManager: VideoFileManager,
) : ViewModel() {

    private val videoList: ImmutableList<VideoItem>
        field = persistentListOf()

    val uiState: RestartableStateFlow<DownloadableUiState> = flow {
        emit(
            GalleryUiState(
                data = videoList,
            )
        )
    }.restartableStateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = DownloadableUiState.Loading
    )

    fun updateVideoList(videoUris: List<String>) {
        viewModelScope.launch {
            videoList.clear()
            videoList.addAll(videoFileManager.getVideoInfoList(videoUris))
            retryGetUiState()
        }
    }

    private fun retryGetUiState() {
        uiState.restart()
    }
}

data class GalleryUiState(
    override val data: ImmutableList<VideoItem>,
) : DownLoadedUiState<ImmutableList<VideoItem>>()

data class VideoItem(
    val uri: String,
    val title: String,
    val duration: Long,
    val thumbnailPath: String?
) 