package jinproject.aideo.library.model

import androidx.annotation.StringRes
import jinproject.aideo.design.R

enum class SortOption(
    @field:StringRes val labelResId: Int
) {
    NEWEST(R.string.library_sort_newest),
    OLDEST(R.string.library_sort_oldest);
}
