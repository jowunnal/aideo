package jinproject.aideo.gallery.gallery

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.media.AndroidMediaFileManager
import jinproject.aideo.core.utils.RestartableStateFlow
import jinproject.aideo.core.utils.restartableStateIn
import jinproject.aideo.data.datasource.local.LocalSettingDataSource
import jinproject.aideo.data.repository.MediaRepository
import jinproject.aideo.design.component.layout.DownLoadedUiState
import jinproject.aideo.design.component.layout.DownloadableUiState
import jinproject.aideo.gallery.gallery.model.GalleryVideoItem
import jinproject.aideo.gallery.gallery.model.VideoStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val androidMediaFileManager: AndroidMediaFileManager,
    private val localSettingDataSource: LocalSettingDataSource,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    val uiState: RestartableStateFlow<DownloadableUiState> = combine(
        localSettingDataSource.getVideoUris(),
        localSettingDataSource.getInferenceTargetLanguage()
    ) { videoUris, language ->
        val videoItems = videoUris.mapNotNull {
            androidMediaFileManager.getVideoInfo(it)
        }

        GalleryUiState(
            data = videoItems.sortedByDescending { it.date }.take(2).map { item ->
                val isSubtitleExist = mediaRepository.checkSubtitleFileExist(item.id)

                GalleryVideoItem.fromVideoItem(
                    videoItem = item,
                    status = VideoStatus.fromSubtitleExistCode(isSubtitleExist)
                )
            }.toImmutableList(),
            languageCode = language
        )
    }.restartableStateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DownloadableUiState.Loading,
    )

    fun onEvent(event: GalleryEvent) {
        when (event) {
            is GalleryEvent.RestartUiState -> restartUiState()
            is GalleryEvent.UpdateVideoSet -> updateVideoList(event.videoUris)
        }
    }

    private fun restartUiState() {
        uiState.restart()
    }

    private fun updateVideoList(videoUris: Set<String>) {
        viewModelScope.launch {
            val cachedVideoList = localSettingDataSource.getVideoUris().first()

            val newVideoList = videoUris.filter { it !in cachedVideoList }

            val targetVideoList = newVideoList + cachedVideoList

            localSettingDataSource.replaceVideoUris(targetVideoList)
        }
    }
}

@Immutable
data class GalleryUiState(
    override val data: ImmutableList<GalleryVideoItem>,
    val languageCode: String,
) : DownLoadedUiState<ImmutableList<GalleryVideoItem>>()

sealed class GalleryEvent {
    data object RestartUiState : GalleryEvent()
    data class UpdateVideoSet(val videoUris: Set<String>) : GalleryEvent()
}