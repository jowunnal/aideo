package jinproject.aideo.gallery

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import jinproject.aideo.core.TopLevelRoute
import jinproject.aideo.gallery.gallery.GalleryScreen
import jinproject.aideo.gallery.setting.SettingScreen
import jinproject.aideo.gallery.submanagement.SubscriptionManagementScreen
import jinproject.aideo.gallery.subs.SubscriptionScreen
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
}

fun NavGraphBuilder.galleryNavGraph(
    navigateToSetting: () -> Unit,
    navigatePopBackStack: () -> Unit,
    navigateToSubscription: () -> Unit,
    navigateToSubscriptionManagement: () -> Unit,
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
    }
}

fun NavController.navigateToGalleryGraph(navOptions: NavOptions?) {
    navigate(GalleryRoute.GalleryGraph, navOptions)
}

fun NavController.navigateToSetting() = navigate(GalleryRoute.Setting)

fun NavController.navigateToSubscription() = navigate(GalleryRoute.Subscription)

fun NavController.navigateToSubscriptionManagement() = navigate(GalleryRoute.SubscriptionManagement)