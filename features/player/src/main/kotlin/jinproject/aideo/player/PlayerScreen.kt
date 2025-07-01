package jinproject.aideo.player

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.PlayPauseButtonState
import androidx.media3.ui.compose.state.PresentationState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPresentationState
import jinproject.aideo.design.component.bar.BackButtonRowScopeAppBar
import jinproject.aideo.design.component.button.clickableAvoidingDuplication
import jinproject.aideo.design.component.layout.hideable.HideableTopBarLayout
import jinproject.aideo.design.component.layout.hideable.SystemBarHidingState
import jinproject.aideo.design.component.layout.hideable.rememberSystemBarHidingState
import jinproject.aideo.design.component.text.AppBarText
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.player.component.PlayPauseButton
import jinproject.aideo.player.component.PlayProgressBar

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    navigatePopBackStack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.releaseExoPlayer()
        }
    }

    val presentationState = rememberPresentationState(viewModel.getExoPlayer())
    val playPauseButtonState = rememberPlayPauseButtonState(viewModel.getExoPlayer())

    PlayerScreen(
        uiState = uiState,
        seekTo = viewModel::seekTo,
        updateLanguageCode = viewModel::updateLanguage,
        navigatePopBackStack = navigatePopBackStack,
        playerSurfaceViewComposable = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .aspectRatio(16f / 9f)
            ) {
                PlayerSurface(
                    player = viewModel.getExoPlayer(),
                    modifier = Modifier.resizeWithContentScale(
                        ContentScale.Fit,
                        presentationState.videoSizeDp
                    ),
                )
            }

            PlayPauseButton(
                state = playPauseButtonState
            )
        }
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    uiState: PlayerUiState,
    density: Density = LocalDensity.current,
    seekTo: (Long) -> Unit,
    updateLanguageCode: (LanguageCode) -> Unit,
    navigatePopBackStack: () -> Unit,
    playerSurfaceViewComposable: @Composable () -> Unit,
) {
    val systemBarHidingState = rememberSystemBarHidingState(
        SystemBarHidingState.Bar.TOPBAR(
            maxHeight = with(density) {
                200.dp.roundToPx()
            },
            minHeight = with(density) {
                84.dp.roundToPx()
            }
        )
    )
    var popUpInfo by remember { mutableStateOf(PopUpInfo(IntOffset(0, 0), false)) }

    if (popUpInfo.visibility)
        Popup(
            offset = popUpInfo.offset,
        ) {
            LanguageCode.entries.toTypedArray().forEach { language ->
                DescriptionMediumText(
                    text = language.name,
                    modifier = Modifier.clickableAvoidingDuplication {
                        updateLanguageCode(language)
                    },
                )
            }
        }

    HideableTopBarLayout(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        systemBarHidingState.animateHideOrAppear()
                    }
                )
            },
        systemBarHidingState = systemBarHidingState,
        topBar = {
            BackButtonRowScopeAppBar(
                onBackClick = navigatePopBackStack
            ) {
                AppBarText(
                    text = "재생",
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { },
                    modifier = Modifier.onGloballyPositioned { layoutCoordinates ->
                        popUpInfo = PopUpInfo(
                            offset = run {
                                val position = layoutCoordinates.positionInWindow()
                                IntOffset(position.x.toInt(), position.y.toInt())
                            },
                            visibility = false,
                        )
                    }) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "언어 설정"
                    )
                }
            }
        }
    ) {
        playerSurfaceViewComposable()

        PlayProgressBar(
            uiState = uiState,
            seekTo = seekTo
        )
    }
}

internal data class PopUpInfo(
    val offset: IntOffset,
    val visibility: Boolean,
)


@Preview
@Composable
private fun PlayerScreenPreview(
    @PreviewParameter(PlayerUiStatePreviewParameter::class)
    playerUiState: PlayerUiState,
) {
    PreviewAideoTheme {
        PlayerScreen(
            uiState = playerUiState,
            seekTo = {},
            updateLanguageCode = {},
            navigatePopBackStack = {},
            playerSurfaceViewComposable = {},
        )
    }
} 