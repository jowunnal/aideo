package jinproject.aideo.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItemColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.navigation.NavDestination
import androidx.navigation.compose.NavHost
import jinproject.aideo.core.TopLevelRoute
import jinproject.aideo.gallery.GalleryRoute
import jinproject.aideo.gallery.galleryNavGraph
import jinproject.aideo.gallery.navigateToSetting
import jinproject.aideo.gallery.navigateToSubscription
import jinproject.aideo.gallery.navigateToSubscriptionManagement
import jinproject.aideo.player.playerNavGraph

@Composable
internal fun NavigationGraph(
    modifier: Modifier = Modifier,
    router: Router,
) {
    val navController = router.navController

    NavHost(
        navController = navController,
        startDestination = GalleryRoute.GalleryGraph,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right)
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right)
        },
    ) {
        galleryNavGraph(
            navigateToSetting = navController::navigateToSetting,
            navigatePopBackStack = navController::popBackStackIfCan,
            navigateToSubscription = navController::navigateToSubscription,
            navigateToSubscriptionManagement = navController::navigateToSubscriptionManagement,
        )

        playerNavGraph(
            navigatePopBackStack = navController::popBackStackIfCan
        )
    }
}

internal fun NavigationSuiteScope.navigationSuiteItems(
    currentDestination: NavDestination?,
    itemColors: NavigationSuiteItemColors,
    onClick: (TopLevelRoute) -> Unit,
) {
    TopLevelRoutes.forEach { destination ->
        val selected = currentDestination.isDestinationInHierarchy(destination)

        item(
            selected = selected,
            onClick = { onClick(destination) },
            icon = {
                if (!selected)
                    Icon(
                        imageVector = ImageVector.vectorResource(id = destination.icon),
                        contentDescription = "clickIcon",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                else
                    Icon(
                        imageVector = ImageVector.vectorResource(id = destination.iconClicked),
                        contentDescription = "clickedIcon",
                        tint = MaterialTheme.colorScheme.primary,
                    )
            },
            colors = itemColors,
        )
    }
}

@Immutable
internal object NavigationDefaults {
    @Composable
    fun navigationIndicatorColor() = MaterialTheme.colorScheme.surface

    @Composable
    fun containerColor() = MaterialTheme.colorScheme.surface

    @Composable
    fun contentColor() = MaterialTheme.colorScheme.onSurface
}