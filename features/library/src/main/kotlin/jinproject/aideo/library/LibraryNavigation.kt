package jinproject.aideo.library

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import jinproject.aideo.core.TopLevelRoute
import kotlinx.serialization.Serializable

@Serializable
sealed class LibraryRoute {
    @Serializable
    data object LibraryGraph : LibraryRoute()

    @Serializable
    data object Library : LibraryRoute(), TopLevelRoute {
        override val icon: Int = jinproject.aideo.design.R.drawable.ic_folder_outlined
        override val iconClicked: Int = jinproject.aideo.design.R.drawable.ic_folder_filled
    }
}

fun NavGraphBuilder.libraryNavGraph() {
    navigation<LibraryRoute.LibraryGraph>(
        startDestination = LibraryRoute.Library
    ) {
        composable<LibraryRoute.Library> {
            LibraryScreen()
        }
    }
}

fun NavController.navigateToLibraryGraph(navOptions: NavOptions? = null) {
    navigate(LibraryRoute.LibraryGraph, navOptions)
}
