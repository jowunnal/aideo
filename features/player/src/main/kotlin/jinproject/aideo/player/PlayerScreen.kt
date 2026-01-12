package jinproject.aideo.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.PresentationState
import androidx.media3.ui.compose.state.rememberPresentationState
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.core.utils.LocalShowRewardAd
import jinproject.aideo.design.component.HorizontalSpacer
import jinproject.aideo.design.component.HorizontalWeightSpacer
import jinproject.aideo.design.component.PopUpInfo
import jinproject.aideo.design.component.button.DefaultIconButton
import jinproject.aideo.design.component.button.clickableAvoidingDuplication
import jinproject.aideo.design.component.effect.RememberEffect
import jinproject.aideo.design.component.lazyList.rememberTimeScheduler
import jinproject.aideo.design.component.text.AppBarText
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.design.utils.tu
import jinproject.aideo.player.PreviewPlayer.seekTo
import jinproject.aideo.player.component.PlayProgressBar
import jinproject.aideo.player.component.PlayerController
import jinproject.aideo.player.component.rememberPlayerControllerState

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    context: Context = LocalContext.current,
    localView: View = LocalView.current,
    configuration: Configuration = LocalConfiguration.current,
    density: Density = LocalDensity.current,
    navigatePopBackStack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val playerControllerState = rememberPlayerControllerState(viewModel.getExoPlayer())
    var visibility by remember { mutableStateOf(false) }
    val timeScheduler = rememberTimeScheduler(
        callBack = {
            visibility = false
        }
    )

    val transitionState = updateTransition(visibility, label = "animateState")
    val localShowRewardAd = LocalShowRewardAd.current

    RememberEffect(visibility) {
        val windowInsetsController = WindowCompat.getInsetsController(
            (localView.context as ComponentActivity).window,
            localView
        )

        if (visibility) {
            timeScheduler.setTime(5000L)

            if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        } else {
            timeScheduler.cancel()

            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    RememberEffect(Unit) {
        (context as Activity).requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        localShowRewardAd {
            viewModel.prepareExoplayer(uiState.currentLanguage)
        }
    }

    PlayerScreen(
        uiState = uiState,
        transitionState = transitionState,
        updateLanguageCode = viewModel::updateSubtitleLanguage,
        updateTransitionState = { visibility = !visibility },
        navigatePopBackStack = navigatePopBackStack,
        playerSurfaceViewComposable = {
            val presentationState: PresentationState =
                rememberPresentationState(viewModel.getExoPlayer(), false)

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
                                visibility = !visibility
                            }
                        )
                    }
                    .align(Alignment.Center)
            ) {

                ContentFrame(
                    player = viewModel.getExoPlayer(),
                    modifier = Modifier.fillMaxSize(),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    if ((uiState.playerState as? PlayerState.Playing)?.subTitle?.isNotBlank() ?: false) {
                        DescriptionMediumText(
                            text = (uiState.playerState as PlayerState.Playing).subTitle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }

                    transitionState.AnimatedVisibility(
                        visible = { it && uiState.playerState is PlayerState.Playing },
                        modifier = Modifier,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        PlayProgressBar(
                            modifier = Modifier,
                            playerState = uiState.playerState as PlayerState.Playing,
                            seekTo = viewModel::seekTo,
                        )
                    }
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
                modifier = Modifier
                    .shadow(
                        1.dp,
                        RoundedCornerShape(20.dp)
                    )
                    .background(
                        MaterialTheme.colorScheme.background,
                        RoundedCornerShape(20.dp)
                    )
            ) {
                LanguageCode.entries.filter { it != LanguageCode.Auto }.toTypedArray()
                    .forEach { language ->
                        DescriptionMediumText(
                            text = language.name,
                            modifier = Modifier
                                .clickableAvoidingDuplication {
                                    updateLanguageCode(language)
                                }
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                                .graphicsLayer {
                                    alpha =
                                        if (language.code == uiState.currentLanguage) 1f else 0.5f
                                },
                        )
                    }
            }
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
                DefaultIconButton(
                    icon = jinproject.aideo.design.R.drawable.ic_arrow_left,
                    onClick = navigatePopBackStack,
                    backgroundTint = Color.Black,
                    iconTint = MaterialTheme.colorScheme.onBackground,
                )
                HorizontalWeightSpacer(1f)
                DefaultIconButton(
                    onClick = {
                        popUpInfo.changeVisibility(!popUpInfo.visibility)
                    },
                    icon = jinproject.aideo.design.R.drawable.ic_translation_language,
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
                    },
                    backgroundTint = Color.Black,
                    iconTint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        if (!LocalInspectionMode.current)
            playerSurfaceViewComposable()
        else {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(vertical = 60.dp)
                    .background(Color.Black)
                    .align(Alignment.Center)
            )
        }
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
            updateLanguageCode = {},
            updateTransitionState = { state = !state },
            navigatePopBackStack = {},
            playerSurfaceViewComposable = {},
        )
    }
}