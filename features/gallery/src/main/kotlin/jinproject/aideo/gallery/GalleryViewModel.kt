package jinproject.aideo.gallery

import android.content.Context
import android.os.Parcelable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jinproject.aideo.core.RestartableStateFlow
import jinproject.aideo.core.WhisperManager
import jinproject.aideo.core.restartableStateIn
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import jinproject.aideo.data.datasource.remote.RemoteGCPDataSource
import jinproject.aideo.data.datasource.remote.model.request.DetectLanguageRequest
import jinproject.aideo.data.repository.GalleryRepository
import jinproject.aideo.design.component.layout.DownLoadedUiState
import jinproject.aideo.design.component.layout.DownloadableUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val galleryRepository: GalleryRepository,
    private val mediaFileManager: MediaFileManager,
    private val whisperManager: WhisperManager,
    private val remoteGCPDataSource: RemoteGCPDataSource,
    private val localPlayerDataSource: LocalPlayerDataSource,
) : ViewModel() {

    private val videoList: ImmutableList<VideoItem>
        field = persistentListOf()

    init {
        viewModelScope.launch {
            whisperManager.load()
        }
    }

    val uiState: RestartableStateFlow<DownloadableUiState> = flow {
        emit(
            GalleryUiState(
                data = videoList,
                languageCode = localPlayerDataSource.getLanguageSetting().first()
            )
        )
    }.restartableStateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = DownloadableUiState.Loading
    )

    private fun retryGetUiState() {
        uiState.restart()
    }

    fun updateVideoList(videoUris: List<String>) {
        viewModelScope.launch {
            videoList.clear()
            videoList.addAll(mediaFileManager.getVideoInfoList(videoUris))
            retryGetUiState()
        }
    }

    fun onClickVideoItem(startTranscribeService: () -> Unit) {
        if (whisperManager.isReady && uiState.value is GalleryUiState) {
            startTranscribeService()
        }
    }

}

data class GalleryUiState(
    override val data: ImmutableList<VideoItem>,
    val languageCode: String,
) : DownLoadedUiState<ImmutableList<VideoItem>>()

@Parcelize
data class VideoItem(
    val uri: String,
    val id: Long,
    val title: String,
    val duration: Long,
    val thumbnailPath: String?,
) : Parcelable