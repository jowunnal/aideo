package jinproject.aideo.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import jinproject.aideo.core.SnackBarMessage
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.core.utils.LocalShowRewardAd
import jinproject.aideo.core.utils.LocalShowSnackBar
import jinproject.aideo.design.component.HorizontalWeightSpacer
import jinproject.aideo.design.component.PopUpInfo
import jinproject.aideo.design.component.button.DefaultIconButton
import jinproject.aideo.design.component.button.clickableAvoidingDuplication
import jinproject.aideo.design.component.effect.RememberEffect
import jinproject.aideo.design.component.lazyList.rememberTimeScheduler
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.design.utils.tu
import jinproject.aideo.player.component.PlayerPopUp
import jinproject.aideo.player.component.PlayerSurfaceViewComposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    context: Context = LocalContext.current,
    localView: View = LocalView.current,
    configuration: Configuration = LocalConfiguration.current,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    navigatePopBackStack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var visibility by remember { mutableStateOf(false) }
    val timeScheduler = rememberTimeScheduler(
        callBack = {
            visibility = false
        }
    )

    val transitionState = updateTransition(visibility, label = "animateState")
    val localShowRewardAd = LocalShowRewardAd.current
    val localShowSnackBar = LocalShowSnackBar.current

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
        localShowRewardAd {
            viewModel.prepareExoplayer()
        }
    }

    BackHandler(true) {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            (context as ComponentActivity).requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            context.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        val windowInsetsController = WindowCompat.getInsetsController(
            (localView.context as ComponentActivity).window,
            localView
        )

        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_DEFAULT

        navigatePopBackStack()
    }

    PlayerScreen(
        uiState = uiState,
        transitionState = transitionState,
        updateLanguageCode = { languageCode ->
            coroutineScope.launch {
                if (!viewModel.isSubtitleExist(languageCode.code)) {
                    viewModel.getExoPlayer()?.stop()
                    localShowSnackBar(
                        SnackBarMessage(
                            headerMessage = context.getString(jinproject.aideo.design.R.string.player_subtitle_conversion_start_header),
                            contentMessage = context.getString(jinproject.aideo.design.R.string.player_subtitle_conversion_start_content)
                        )
                    )
                }
            }

            viewModel.updateSubtitleLanguage(languageCode)
        },
        updateTransitionState = { visibility = !visibility },
        navigatePopBackStack = navigatePopBackStack,
        setTimer = { timeMillis ->
            timeScheduler.setTime(timeMillis)
        },
        playerSurfaceViewComposable = {
            PlayerSurfaceViewComposable(
                uiState = uiState,
                transitionState = transitionState,
                seekTo = viewModel::seekTo,
                getPlayer = viewModel::getExoPlayer,
                updateTransitionState = {
                    visibility = !visibility
                }
            )
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
    setTimer: (Long) -> Unit,
    playerSurfaceViewComposable: @Composable BoxScope.() -> Unit,
) {
    var popUpInfo by remember { mutableStateOf(PopUpInfo()) }
    val iconHeight = with(density) {
        24.dp.roundToPx()
    }
    val popUpHalfWidth = with(density) {
        (28.tu.roundToPx() + 16.dp.roundToPx()) / 2
    }

    PlayerPopUp(
        popUpInfo = popUpInfo,
        uiState = uiState,
        updateLanguageCode = updateLanguageCode,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickableAvoidingDuplication(onClick = updateTransitionState)
            .padding(vertical = 12.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.changes.any { it.isConsumed })
                        setTimer(5000)
                }
            }
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
                    iconTint = Color.White,
                )
                HorizontalWeightSpacer(1f)
                DefaultIconButton(
                    onClick = {
                        popUpInfo.updateVisibility(true)
                    },
                    icon = jinproject.aideo.design.R.drawable.ic_translation_language,
                    modifier = Modifier.onGloballyPositioned { layoutCoordinates ->
                        val position = layoutCoordinates.positionInWindow()

                        popUpInfo.updateOffset(
                            IntOffset(
                                position.x.toInt() - popUpHalfWidth,
                                position.y.toInt() + iconHeight
                            )
                        )
                    },
                    backgroundTint = Color.Black,
                    iconTint = Color.White,
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
            setTimer = {},
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
            setTimer = {},
            playerSurfaceViewComposable = {},
        )
    }
}