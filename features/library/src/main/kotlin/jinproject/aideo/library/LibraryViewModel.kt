package jinproject.aideo.library

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.media.AndroidMediaFileManager
import jinproject.aideo.data.datasource.local.LocalSettingDataSource
import jinproject.aideo.library.model.LibraryVideoItem
import jinproject.aideo.library.model.SortOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val androidMediaFileManager: AndroidMediaFileManager,
    private val localSettingDataSource: LocalSettingDataSource,
) : ViewModel() {

    private val sortOption = MutableStateFlow(SortOption.NEWEST)

    val uiState: StateFlow<LibraryUiState> = combine(
        localSettingDataSource.getVideoUris(),
        sortOption
    ) { videoUris, sort ->
        val videoItems = videoUris.mapNotNull {
            androidMediaFileManager.getVideoInfo(it)
        }.map { item ->
            LibraryVideoItem(
                uri = item.uri,
                id = item.id,
                thumbnailPath = item.thumbnailPath,
                date = item.date
            )
        }.let { items ->
            when (sort) {
                SortOption.NEWEST -> items.sortedByDescending { it.date }
                SortOption.OLDEST -> items.sortedBy { it.date }
            }
        }

        LibraryUiState(
            data = videoItems.toImmutableList(),
            sortOption = sort
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState.getDefault(),
    )

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.UpdateSortOption -> sortOption.value = event.sortOption
            is LibraryEvent.RemoveVideoUris -> removeVideoUris(event.videoUris)
        }
    }

    private fun removeVideoUris(uris: Set<String>) {
        viewModelScope.launch {
            localSettingDataSource.removeVideoUris(uris)
            uris.onEach { uri ->
                context.contentResolver.releasePersistableUriPermission(
                    uri.toUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }
}

@Immutable
data class LibraryUiState(
    val data: ImmutableList<LibraryVideoItem>,
    val sortOption: SortOption,
) {
    companion object {
        fun getDefault(): LibraryUiState = LibraryUiState(
            data = persistentListOf(),
            sortOption = SortOption.NEWEST
        )
    }
}

sealed class LibraryEvent {
    data class UpdateSortOption(val sortOption: SortOption) : LibraryEvent()
    data class RemoveVideoUris(val videoUris: Set<String>) : LibraryEvent()
}

@Stable
class VideoItemSelection {
    var isSelectionMode by mutableStateOf(false)
        private set

    val selectedUris = mutableStateSetOf<String>()

    fun addSelectedUri(uri: String) {
        selectedUris.add(uri)
    }

    fun removeSelectedUri(uri: String) {
        selectedUris.remove(uri)
    }

    fun updateIsSelectionMode(bool: Boolean) {
        isSelectionMode = bool
    }
}
