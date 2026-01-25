package jinproject.aideo.player.component

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.PlayPauseButtonState
import androidx.media3.ui.compose.state.SeekBackButtonState
import androidx.media3.ui.compose.state.SeekForwardButtonState
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.player.PlayerState
import jinproject.aideo.player.PlayerUiStatePreviewParameter.Companion.getPreviewPlayerControllerState
import kotlinx.coroutines.launch

internal data class PlayerControllerState @OptIn(UnstableApi::class) constructor(
    val playPauseButtonState: PlayPauseButtonState,
    val seekBackButtonState: SeekBackButtonState,
    val seekForwardButtonState: SeekForwardButtonState,
)

@OptIn(UnstableApi::class)
@Composable
internal fun rememberPlayerControllerState(player: Player?): PlayerControllerState? {
    val playerControllerState: PlayerControllerState? = remember(player) {
        player?.let {
            PlayerControllerState(
                playPauseButtonState = PlayPauseButtonState(player),
                seekBackButtonState = SeekBackButtonState(player),
                seekForwardButtonState = SeekForwardButtonState(player),
            )
        }
    }

    LaunchedEffect(player) {
        playerControllerState?.let { state ->
            launch {
                state.playPauseButtonState.observe()
            }
            launch {
                state.seekBackButtonState.observe()
            }
            launch {
                state.seekForwardButtonState.observe()
            }
        }
    }

    return playerControllerState
}


@OptIn(UnstableApi::class)
@Composable
internal fun PlayerController(
    modifier: Modifier = Modifier,
    playerControllerState: PlayerControllerState?,
    transitionState: Transition<Boolean>,
) {
    transitionState.AnimatedVisibility(
        visible = { it && playerControllerState != null },
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        playerControllerState?.let { state ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SeekBackButton(
                        state = state.seekBackButtonState,
                        modifier = Modifier.weight(1f)
                    )
                    PlayPauseButton(
                        state = state.playPauseButtonState,
                        modifier = Modifier.weight(1f)
                    )
                    SeekForwardButton(
                        state = state.seekForwardButtonState,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

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
            imageVector = if (!state.showPlay) ImageVector.vectorResource(
                jinproject.aideo.design.R.drawable.ic_playback_pause
            )
            else ImageVector.vectorResource(jinproject.aideo.design.R.drawable.ic_playback_play),
            contentDescription = if (!state.showPlay) stringResource(jinproject.aideo.design.R.string.content_desc_pause) else stringResource(jinproject.aideo.design.R.string.content_desc_play),
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun SeekBackButton(
    state: SeekBackButtonState,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = state::onClick,
        modifier = modifier,
        enabled = state.isEnabled,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(
                jinproject.aideo.design.R.drawable.ic_playback_back
            ),
            contentDescription = stringResource(jinproject.aideo.design.R.string.content_desc_seek_back, (state.seekBackAmountMs / 1000).toInt()),
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun SeekForwardButton(
    state: SeekForwardButtonState,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = state::onClick,
        modifier = modifier,
        enabled = state.isEnabled,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(
                jinproject.aideo.design.R.drawable.ic_playback_next
            ),
            contentDescription = stringResource(jinproject.aideo.design.R.string.content_desc_seek_forward, (state.seekForwardAmountMs / 1000).toInt()),
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayProgressBar(
    modifier: Modifier = Modifier,
    playerState: PlayerState.Ready,
    seekTo: (Long) -> Unit,
) {
    Slider(
        value = (playerState.currentPosition).toFloat(),
        onValueChange = { position ->
            playerState.updateCurrentPos(position.toLong())
            seekTo(position.toLong())
        },
        valueRange = 0f..(playerState.duration).toFloat(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.onPrimary,
            activeTrackColor = MaterialTheme.colorScheme.onPrimary,
            inactiveTrackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
        )
    )
}

@OptIn(UnstableApi::class)
@Preview
@Composable
private fun PreviewPlayerController() = PreviewAideoTheme {
    Column(
        modifier = Modifier
            .height(200.dp)
            .background(MaterialTheme.colorScheme.primary)
    ) {
        PlayerController(
            modifier = Modifier
                .fillMaxHeight()
                .wrapContentHeight(),
            playerControllerState = getPreviewPlayerControllerState(),
            transitionState = updateTransition(true),
        )
    }
}