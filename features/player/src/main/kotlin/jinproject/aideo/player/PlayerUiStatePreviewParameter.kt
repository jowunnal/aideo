package jinproject.aideo.player

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

class PlayerUiStatePreviewParameter: PreviewParameterProvider<PlayerUiState> {
    override val values: Sequence<PlayerUiState>
        get() = sequenceOf(
            PlayerUiState(
                playingState = PlayingState(),
                currentLanguage = "",
            )
        )
}

