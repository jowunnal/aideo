package jinproject.aideo.gallery.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jinproject.aideo.core.media.AndroidMediaFileManager
import jinproject.aideo.core.media.VideoItem
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.core.utils.RestartableStateFlow
import jinproject.aideo.core.utils.restartableStateIn
import jinproject.aideo.data.datasource.local.LocalSettingDataSource
import jinproject.aideo.design.component.layout.DownLoadedUiState
import jinproject.aideo.design.component.layout.DownloadableUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val androidMediaFileManager: AndroidMediaFileManager,
    private val localSettingDataSource: LocalSettingDataSource,
) : ViewModel() {

    val uiState: RestartableStateFlow<DownloadableUiState> = combine(
        localSettingDataSource.getVideoUris(),
        localSettingDataSource.getInferenceTargetLanguage()
    ) { videoUris, language ->
        val videoItems = videoUris.mapNotNull {
            androidMediaFileManager.getVideoInfo(it)
        }

        GalleryUiState(
            data = videoItems.toImmutableList(),
            languageCode = language
        )
    }.restartableStateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DownloadableUiState.Loading,
    )

    fun updateVideoList(videoUris: List<String>) {
        viewModelScope.launch {
            val cachedVideoList = localSettingDataSource.getVideoUris().first()

            val newVideoList = videoUris.filter { it !in cachedVideoList }

            val targetVideoList = newVideoList + cachedVideoList

            localSettingDataSource.replaceVideoUris(targetVideoList)
        }
    }

    fun updateLanguage(languageCode: LanguageCode) {
        viewModelScope.launch {
            localSettingDataSource.setInferenceTargetLanguage(languageCode.code)
        }
    }
}

data class GalleryUiState(
    override val data: ImmutableList<VideoItem>,
    val languageCode: String,
) : DownLoadedUiState<ImmutableList<VideoItem>>()