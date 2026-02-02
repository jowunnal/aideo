package jinproject.aideo.gallery.gallery.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import jinproject.aideo.data.repository.MediaRepository
import jinproject.aideo.design.R
import jinproject.aideo.design.theme.AideoColor

@Immutable
enum class VideoStatus(@StringRes val displayRes: Int, val color: AideoColor) {
    COMPLETED(R.string.video_status_completed, AideoColor.primary),
    NEED_TRANSLATE(R.string.video_status_need_translate, AideoColor.amber_400),
    NEED_INFERENCE(R.string.video_status_need_inference, AideoColor.red);

    companion object {
        fun fromSubtitleExistCode(code: Int): VideoStatus {
            return when (code) {
                MediaRepository.EXIST -> COMPLETED
                MediaRepository.NEED_TRANSLATE -> NEED_TRANSLATE
                MediaRepository.NEED_INFERENCE -> NEED_INFERENCE
                else -> throw IllegalArgumentException("[$code] is not supported in subtitleExistCode.")
            }
        }
    }
}