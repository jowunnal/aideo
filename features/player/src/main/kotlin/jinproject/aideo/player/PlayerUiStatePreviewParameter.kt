package jinproject.aideo.player

import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.PlayPauseButtonState
import androidx.media3.ui.compose.state.SeekBackButtonState
import androidx.media3.ui.compose.state.SeekForwardButtonState
import jinproject.aideo.player.component.PlayerControllerState

@UnstableApi
class PlayerUiStatePreviewParameter : PreviewParameterProvider<PlayerUiState> {
    override val values: Sequence<PlayerUiState>
        get() = sequenceOf(
            PlayerUiState(
                playerState = PlayerState.Ready(),
                currentLanguage = "",
            )
        )

    companion object {
        @OptIn(UnstableApi::class)
        internal fun getPreviewPlayerControllerState(): PlayerControllerState {
            return PlayerControllerState(
                playPauseButtonState = PlayPauseButtonState(PreviewPlayer),
                seekBackButtonState = SeekBackButtonState(PreviewPlayer),
                seekForwardButtonState = SeekForwardButtonState(PreviewPlayer),
            )
        }
    }
}

@UnstableApi
internal object PreviewPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
    override fun getState(): State {
        return State.Builder().build()
    }
}

