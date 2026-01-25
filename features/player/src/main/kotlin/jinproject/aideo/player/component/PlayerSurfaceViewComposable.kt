package jinproject.aideo.player.component

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Transition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.PresentationState
import androidx.media3.ui.compose.state.rememberPresentationState
import jinproject.aideo.design.component.button.DefaultIconButton
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.player.PlayerState
import jinproject.aideo.player.PlayerUiState

@OptIn(UnstableApi::class)
@Composable
internal fun BoxScope.PlayerSurfaceViewComposable(
    uiState: PlayerUiState,
    transitionState: Transition<Boolean>,
    density: Density = LocalDensity.current,
    configuration: Configuration = LocalConfiguration.current,
    context: Context = LocalContext.current,
    getPlayer: () -> ExoPlayer?,
    seekTo: (Long) -> Unit,
    updateTransitionState: () -> Unit,
) {
    val playerControllerState = rememberPlayerControllerState(getPlayer())
    val presentationState: PresentationState =
        rememberPresentationState(getPlayer(), false)

    Box(
        modifier = Modifier
            .resizeWithContentScale(
                contentScale = ContentScale.Fit,
                presentationState.videoSizeDp
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val centerXPos = with(density) {
                            configuration.screenWidthDp.dp.toPx() / 2
                        }

                        if (offset.x > centerXPos)
                            playerControllerState?.seekForwardButtonState?.onClick()
                        else
                            playerControllerState?.seekBackButtonState?.onClick()
                    },
                    onTap = {
                        updateTransitionState()
                    }
                )
            }
            .align(Alignment.Center)
    ) {

        ContentFrame(
            player = getPlayer(),
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            if ((uiState.playerState as? PlayerState.Ready)?.subTitle?.isNotBlank()
                    ?: false
            ) {
                DescriptionMediumText(
                    text = uiState.playerState.subTitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(),
                    textAlign = TextAlign.Center,
                    color = Color.White,
                )
            }

            transitionState.AnimatedVisibility(
                visible = { it && uiState.playerState is PlayerState.Ready },
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                PlayProgressBar(
                    modifier = Modifier,
                    playerState = uiState.playerState as PlayerState.Ready,
                    seekTo = seekTo,
                )
            }
        }

        PlayerController(
            modifier = Modifier
                .align(Alignment.Center),
            playerControllerState = playerControllerState,
            transitionState = transitionState,
        )

        transitionState.AnimatedVisibility(
            visible = { it },
            modifier = Modifier.align(Alignment.TopEnd),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            DefaultIconButton(
                icon = jinproject.aideo.design.R.drawable.ic_rotate_screen,
                onClick = {
                    (context as Activity).requestedOrientation =
                        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                },
                backgroundTint = Color.Transparent,
                iconTint = Color.White,
            )
        }
    }
}