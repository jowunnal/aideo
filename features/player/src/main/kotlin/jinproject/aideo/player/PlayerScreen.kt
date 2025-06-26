package jinproject.aideo.player

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import jinproject.aideo.design.component.layout.DefaultLayout
import jinproject.aideo.design.component.paddingvalues.addStatusBarPadding
import jinproject.aideo.design.utils.PreviewAideoTheme

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoUri: String,
    viewModel: PlayerViewModel = hiltViewModel(),
    context: Context = LocalContext.current,
    navigatePopBackStack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val exoPlayerManager = remember { ExoPlayerManager(context) }

    val playerState by exoPlayerManager.playerState.collectAsStateWithLifecycle()
    val presentationState = rememberPresentationState(exoPlayerManager.getExoPlayer())

    LaunchedEffect(videoUri) {
        if (videoUri.isNotEmpty()) {
            viewModel.initializePlayer(videoUri) {
                exoPlayerManager.initializePlayer(videoUri)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayerManager.release()
        }
    }

    DefaultLayout(
        topBar = {
            TopAppBar(
                title = { Text(text = "재생") },
                navigationIcon = {
                    IconButton(onClick = navigatePopBackStack) {
                        Icon(
                            imageVector = ImageVector.vectorResource(jinproject.aideo.design.R.drawable.ic_arrow_left),
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showLanguageMenu() }) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "언어 설정"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // 비디오 플레이어
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .aspectRatio(16f / 9f)
            ) {
                PlayerSurface(
                    player = exoPlayerManager.getExoPlayer(),
                    modifier = Modifier.resizeWithContentScale(
                        ContentScale.Fit,
                        presentationState.videoSizeDp
                    ),
                )
            }

            // 재생 컨트롤
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        viewModel.previousVideo { newVideoUri ->
                            exoPlayerManager.initializePlayer(newVideoUri)
                        }
                    }
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(jinproject.aideo.design.R.drawable.ic_playback_back),
                        contentDescription = "이전 비디오"
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.togglePlayPause {
                            exoPlayerManager.togglePlayPause()
                        }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icons
                    Icon(
                        imageVector = if (playerState.isPlaying) ImageVector.vectorResource(
                            jinproject.aideo.design.R.drawable.ic_playback_pause
                        )
                        else ImageVector.vectorResource(jinproject.aideo.design.R.drawable.ic_playback_play),
                        contentDescription = if (playerState.isPlaying) "일시정지" else "재생",
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.nextVideo { newVideoUri ->
                            exoPlayerManager.initializePlayer(newVideoUri)
                        }
                    }
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(jinproject.aideo.design.R.drawable.ic_playback_next),
                        contentDescription = "다음 비디오"
                    )
                }
            }

            // Seek Bar
            Slider(
                value = playerState.currentPosition.toFloat(),
                onValueChange = { position ->
                    viewModel.seekTo(position.toLong()) { seekPosition ->
                        exoPlayerManager.seekTo(seekPosition)
                    }
                },
                valueRange = 0f..playerState.duration.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        // 언어 설정 메뉴
        if (uiState.showLanguageMenu) {
            LanguageMenu(
                onLanguageSelected = { languageCode ->
                    viewModel.setLanguage(languageCode)
                    viewModel.hideLanguageMenu()
                },
                onDismiss = { viewModel.hideLanguageMenu() }
            )
        }
    }
}

@Composable
private fun LanguageMenu(
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss
    ) {
        listOf(
            "ko" to "한국어",
            "en" to "English",
            "ja" to "日本語",
            "zh-CN" to "中文(简体)",
            "zh-TW" to "中文(繁體)"
        ).forEach { (code, name) ->
            DropdownMenuItem(
                text = { Text(text = name) },
                onClick = { onLanguageSelected(code) }
            )
        }
    }
}

@Preview
@Composable
private fun PlayerScreenPreview() {
    PreviewAideoTheme {
        PlayerScreen(
            videoUri = "",
            navigatePopBackStack = {},
        )
    }
} 