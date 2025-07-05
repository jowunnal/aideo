package jinproject.aideo.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import jinproject.aideo.design.component.button.clickableAvoidingDuplication
import jinproject.aideo.design.component.effect.RememberEffect
import jinproject.aideo.design.component.lazyList.rememberTimeScheduler
import jinproject.aideo.design.component.text.AppBarText
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.design.utils.tu
import jinproject.aideo.player.component.PlayProgressBar
import jinproject.aideo.player.component.PlayerController
import jinproject.aideo.player.component.rememberPlayerControllerState

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

    val playerControllerState = rememberPlayerControllerState(viewModel.getExoPlayer())
    var visibility by remember { mutableStateOf(false) }
    val timeScheduler = rememberTimeScheduler(
        callBack = {
            visibility = false
        }
    )

    val transitionState = updateTransition(visibility, label = "animateState")

    RememberEffect(visibility) {
        if (visibility)
            timeScheduler.setTime(5000L)
        else
            timeScheduler.cancel()
    }

    PlayerScreen(
        uiState = uiState,
        transitionState = transitionState,
        seekTo = viewModel::seekTo,
        updateLanguageCode = viewModel::updateLanguage,
        updateTransitionState = { visibility = !visibility },
        navigatePopBackStack = navigatePopBackStack,
        playerSurfaceViewComposable = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 60.dp)
                    .aspectRatio(2f / 3f)
                    .align(Alignment.Center)
            ) {

                PlayerSurface(
                    player = viewModel.getExoPlayer(),
                    modifier = Modifier.fillMaxSize(),
                )

                if (uiState.playingState.subTitle.isNotBlank()) {
                    DescriptionMediumText(
                        text = uiState.playingState.subTitle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                            .padding(bottom = 12.dp)
                            .align(Alignment.BottomCenter),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                PlayerController(
                    modifier = Modifier
                        .align(Alignment.Center),
                    playerControllerState = playerControllerState,
                    transitionState = transitionState,
                )
            }
        }
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerScreen(
    uiState: PlayerUiState,
    transitionState: Transition<Boolean>,
    density: Density = LocalDensity.current,
    seekTo: (Long) -> Unit,
    updateLanguageCode: (LanguageCode) -> Unit,
    updateTransitionState: () -> Unit,
    navigatePopBackStack: () -> Unit,
    playerSurfaceViewComposable: @Composable BoxScope.() -> Unit,
) {
    var popUpInfo by remember { mutableStateOf(PopUpInfo(IntOffset(0, 0))) }
    val iconHeight = with(density) {
        24.dp.roundToPx()
    }
    val popUpHalfWidth = with(density) {
        (28.tu.roundToPx() + 16.dp.roundToPx()) / 2
    }

    if (popUpInfo.visibility)
        Popup(
            offset = popUpInfo.offset,
            onDismissRequest = {
                popUpInfo.changeVisibility(false)
            }
        ) {
            Column(
                modifier = Modifier.background(MaterialTheme.colorScheme.background)
            ) {
                LanguageCode.entries.toTypedArray().forEach { language ->
                    DescriptionMediumText(
                        text = language.name,
                        modifier = Modifier
                            .clickableAvoidingDuplication {
                                updateLanguageCode(language)
                            }
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                    )
                }
            }
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
            .clickableAvoidingDuplication {
                updateTransitionState()
            }
            .padding(vertical = 12.dp)
    ) {
        transitionState.AnimatedVisibility(
            visible = { it },
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = navigatePopBackStack,
                    modifier = Modifier
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = jinproject.aideo.design.R.drawable.ic_arrow_left),
                        contentDescription = "뒤로 가기",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                AppBarText(
                    text = "재생",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                IconButton(
                    onClick = {
                        popUpInfo.changeVisibility(!popUpInfo.visibility)
                    },
                    modifier = Modifier.onGloballyPositioned { layoutCoordinates ->
                        popUpInfo = PopUpInfo(
                            offset = run {
                                val position = layoutCoordinates.positionInParent()

                                IntOffset(
                                    position.x.toInt() - popUpHalfWidth,
                                    position.y.toInt() + iconHeight
                                )
                            }
                        )
                    }) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "언어 설정",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        if (!LocalInspectionMode.current)
            playerSurfaceViewComposable()
        else {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(vertical = 60.dp)
                    .aspectRatio(2f / 3f)
                    .background(Color.Black)
                    .align(Alignment.Center)
            )
        }

        transitionState.AnimatedVisibility(
            visible = { it },
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PlayProgressBar(
                playingState = uiState.playingState,
                seekTo = seekTo,
            )
        }
    }
}

internal data class PopUpInfo(val offset: IntOffset) {
    var visibility: Boolean by mutableStateOf(false)
        private set

    fun changeVisibility(visible: Boolean) {
        visibility = visible
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Preview
@Composable
private fun PlayerScreenPreview(
    @PreviewParameter(PlayerUiStatePreviewParameter::class)
    playerUiState: PlayerUiState,
) {
    var state by remember { mutableStateOf(false) }

    PreviewAideoTheme {
        PlayerScreen(
            uiState = playerUiState,
            transitionState = updateTransition(state),
            seekTo = {},
            updateLanguageCode = {},
            updateTransitionState = { state = !state },
            navigatePopBackStack = {},
            playerSurfaceViewComposable = {},
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Preview
@Composable
private fun PlayerScreenTruePreview(
    @PreviewParameter(PlayerUiStatePreviewParameter::class)
    playerUiState: PlayerUiState,
) {
    var state by remember { mutableStateOf(true) }

    PreviewAideoTheme {
        PlayerScreen(
            uiState = playerUiState,
            transitionState = updateTransition(state),
            seekTo = {},
            updateLanguageCode = {},
            updateTransitionState = { state = !state },
            navigatePopBackStack = {},
            playerSurfaceViewComposable = {},
        )
    }
}