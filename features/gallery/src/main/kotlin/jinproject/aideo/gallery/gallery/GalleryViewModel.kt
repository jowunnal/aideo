package jinproject.aideo.gallery.gallery

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.io.Files.map
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.media.AndroidMediaFileManager
import jinproject.aideo.core.media.VideoItem
import jinproject.aideo.gallery.gallery.model.GalleryVideoItem
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
    @param:ApplicationContext private val context: Context,
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
            data = videoItems.sortedByDescending { it.date }.take(2).map { item ->
                GalleryVideoItem(
                    uri = item.uri,
                    id = item.id,
                    thumbnailPath = item.thumbnailPath,
                    date = item.date
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
            is GalleryEvent.UpdateVideoSet -> updateVideoList(event.videoUris)
            is GalleryEvent.RemoveVideoSet -> removeVideoList(event.videoUris)
            is GalleryEvent.UpdateLanguage -> updateLanguage(event.languageCode)
        }
    }

    private fun updateVideoList(videoUris: Set<String>) {
        viewModelScope.launch {
            val cachedVideoList = localSettingDataSource.getVideoUris().first()

            val newVideoList = videoUris.filter { it !in cachedVideoList }

            val targetVideoList = newVideoList + cachedVideoList

            localSettingDataSource.replaceVideoUris(targetVideoList)
        }
    }

    private fun removeVideoList(videoUris: Set<String>) {
        viewModelScope.launch {
            localSettingDataSource.removeVideoUris(videoUris)
            videoUris.onEach { uri ->
                context.contentResolver.releasePersistableUriPermission(
                    uri.toUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    private fun updateLanguage(languageCode: LanguageCode) {
        viewModelScope.launch {
            localSettingDataSource.setInferenceTargetLanguage(languageCode.code)
        }
    }
}

@Stable
data class GalleryUiState(
    override val data: ImmutableList<GalleryVideoItem>,
    val languageCode: String,
) : DownLoadedUiState<ImmutableList<GalleryVideoItem>>()

sealed class GalleryEvent {
    data class UpdateVideoSet(val videoUris: Set<String>) : GalleryEvent()
    data class RemoveVideoSet(val videoUris: Set<String>) : GalleryEvent()
    data class UpdateLanguage(val languageCode: LanguageCode) : GalleryEvent()
}