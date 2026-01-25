package jinproject.aideo.gallery.setting

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

class SettingUiStatePreviewParameter: PreviewParameterProvider<SettingUiState> {
    override val values: Sequence<SettingUiState> get() = sequenceOf(
        SettingUiState.default()
    )
}