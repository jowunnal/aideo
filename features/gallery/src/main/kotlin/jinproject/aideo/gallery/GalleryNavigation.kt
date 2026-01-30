package jinproject.aideo.gallery

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import jinproject.aideo.core.TopLevelRoute
import jinproject.aideo.gallery.gallery.GalleryScreen
import jinproject.aideo.gallery.setting.SettingScreen
import jinproject.aideo.gallery.submanagement.SubscriptionManagementScreen
import jinproject.aideo.gallery.subs.SubscriptionScreen
import jinproject.aideo.gallery.term.TermScreen
import kotlinx.serialization.Serializable

@Serializable
sealed class GalleryRoute {
    @Serializable
    data object GalleryGraph : GalleryRoute()

    @Serializable
    data object Gallery : GalleryRoute(), TopLevelRoute {
        override val icon: Int = jinproject.aideo.design.R.drawable.icon_simulator
        override val iconClicked: Int = jinproject.aideo.design.R.drawable.icon_simulator
    }

    @Serializable
    data object Setting : GalleryRoute()

    @Serializable
    data object Subscription : GalleryRoute()

    @Serializable
    data object SubscriptionManagement : GalleryRoute()

    @Serializable
    data object Term : GalleryRoute()
}

fun NavGraphBuilder.galleryNavGraph(
    navigateToSetting: () -> Unit,
    navigatePopBackStack: () -> Unit,
    navigateToSubscription: () -> Unit,
    navigateToSubscriptionManagement: () -> Unit,
    navigateToTerm: () -> Unit,
) {
    navigation<GalleryRoute.GalleryGraph>(
        startDestination = GalleryRoute.Gallery
    ) {
        composable<GalleryRoute.Gallery> {
            GalleryScreen(
                navigateToSetting = navigateToSetting,
                navigateToSubscription = navigateToSubscription
            )
        }
        composable<GalleryRoute.Setting> {
            SettingScreen(
                navigatePopBackStack = navigatePopBackStack,
                navigateToSubscriptionManagement = navigateToSubscriptionManagement,
                navigateToSubscription = navigateToSubscription,
                navigateToTerm = navigateToTerm,
            )
        }
        composable<GalleryRoute.Subscription> {
            SubscriptionScreen(
                navigatePopBackStack = navigatePopBackStack,
                navigateToSubscriptionManagement = navigateToSubscriptionManagement
            )
        }
        composable<GalleryRoute.SubscriptionManagement> {
            SubscriptionManagementScreen(
                navigatePopBackStack = navigatePopBackStack,
                navigateToSubscription = navigateToSubscription,
            )
        }
        composable<GalleryRoute.Term> {
            TermScreen(
                navigatePopBackStack = navigatePopBackStack,
            )
        }
    }
}

fun NavController.navigateToSetting() = navigate(GalleryRoute.Setting)

fun NavController.navigateToSubscription() = navigateSingleInstance(GalleryRoute.Subscription)

fun NavController.navigateToSubscriptionManagement() =
    navigateSingleInstance(GalleryRoute.SubscriptionManagement)

private fun NavController.navigateSingleInstance(route: GalleryRoute) {
    val popped = popBackStack(route, inclusive = false)

    if (!popped)
        navigate(route)
}

fun NavController.navigateToTerm() = navigate(GalleryRoute.Term)