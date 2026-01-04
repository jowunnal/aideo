package jinproject.aideo.player

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import jinproject.aideo.player.PlayerRoute.Companion.VIDEO_URI
import kotlinx.serialization.Serializable

@Serializable
sealed class PlayerRoute {
    @Serializable
    data class Player(val videoUri: String) : PlayerRoute()

    companion object {
        const val VIDEO_URI = "videoUri"
    }
}

fun NavGraphBuilder.playerNavGraph(
    navigatePopBackStack: () -> Unit,
) {
    composable<PlayerRoute.Player>(
        deepLinks = listOf(
            navDeepLink<PlayerRoute.Player>(
                basePath = "aideo://app/player"
            ),
        )
    ) {
        PlayerScreen(
            navigatePopBackStack = navigatePopBackStack,
        )
    }
}

fun NavController.navigateToPlayerGraph(videoUri: String, navOptions: NavOptions?) {
    navigate(PlayerRoute.Player(videoUri), navOptions)
} 