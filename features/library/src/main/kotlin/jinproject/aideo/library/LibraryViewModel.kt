package jinproject.aideo.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val androidMediaFileManager: AndroidMediaFileManager,
    localSettingDataSource: LocalSettingDataSource,
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
        }
    }
}

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
}
