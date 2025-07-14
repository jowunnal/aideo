package jinproject.aideo.gallery

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import jinproject.aideo.core.TopLevelRoute
import jinproject.aideo.core.utils.parseUri
import kotlinx.serialization.Serializable

@Serializable
sealed class GalleryRoute {
    @Serializable
    data object GalleryGraph: GalleryRoute()

    @Serializable
    data object Gallery : GalleryRoute(), TopLevelRoute {
        override val icon: Int = jinproject.aideo.design.R.drawable.icon_simulator
        override val iconClicked: Int = jinproject.aideo.design.R.drawable.icon_simulator
    }
}

fun NavGraphBuilder.galleryNavGraph(
    navigateToPlayerGraph: (videoUri: String, navOptions: NavOptions?) -> Unit,
) {
    navigation<GalleryRoute.GalleryGraph>(
        startDestination = GalleryRoute.Gallery
    ) {
        composable<GalleryRoute.Gallery> {
            GalleryScreen(
                navigateToPlayer = { videoUri ->
                    navigateToPlayerGraph(videoUri.parseUri(), null)
                }
            )
        }
    }
}

fun NavController.navigateToGalleryGraph(navOptions: NavOptions?) {
    navigate(GalleryRoute.GalleryGraph, navOptions)
}