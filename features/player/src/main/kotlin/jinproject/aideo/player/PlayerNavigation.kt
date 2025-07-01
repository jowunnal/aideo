package jinproject.aideo.player

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import androidx.navigation.toRoute
import jinproject.aideo.core.TopLevelRoute
import jinproject.aideo.core.toOriginUri
import jinproject.aideo.player.PlayerRoute.Companion.VIDEO_URI
import kotlinx.serialization.Serializable

@Serializable
sealed class PlayerRoute {
    @Serializable
    data object PlayerGraph: PlayerRoute()

    @Serializable
    data class Player(val videoUri: String) : PlayerRoute()

    companion object {
        const val VIDEO_URI = "videoUri"
    }
}

fun NavGraphBuilder.playerNavGraph(
    navigatePopBackStack: () -> Unit,
) {
    navigation<PlayerRoute.PlayerGraph>(
        startDestination = PlayerRoute.Player(""),
    ) {
        composable<PlayerRoute.Player>(
            deepLinks = listOf(
                navDeepLink<PlayerRoute.Player>(
                    basePath = "aideo://player/player"
                )
            )
        ) { backStackEntry ->
            PlayerScreen(
                navigatePopBackStack = navigatePopBackStack,
            )
        }
    }
}

fun NavController.navigateToPlayerGraph(videoUri: String, navOptions: NavOptions?) {
    navigate("${PlayerRoute.PlayerGraph}?${VIDEO_URI}=$videoUri", navOptions)
} 