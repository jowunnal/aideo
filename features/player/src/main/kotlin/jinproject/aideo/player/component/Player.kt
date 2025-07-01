package jinproject.aideo.player.component

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.PlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import jinproject.aideo.player.PlayerUiState

@OptIn(UnstableApi::class)
@Composable
internal fun PlayPauseButton(
    state: PlayPauseButtonState,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = state::onClick,
        modifier = modifier,
        enabled = state.isEnabled,
    ) {
        Icon(
            imageVector = if (state.showPlay) ImageVector.vectorResource(
                jinproject.aideo.design.R.drawable.ic_playback_pause
            )
            else ImageVector.vectorResource(jinproject.aideo.design.R.drawable.ic_playback_play),
            contentDescription = if (state.showPlay) "일시정지" else "재생",
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
internal fun PlayProgressBar(
    uiState: PlayerUiState,
    seekTo: (Long) -> Unit,
) {
    Slider(
        value = uiState.playingState.currentPosition.toFloat(),
        onValueChange = { position ->
            seekTo(position.toLong())
        },
        valueRange = 0f..uiState.playingState.duration.toFloat(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    )
}