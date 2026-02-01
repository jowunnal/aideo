package jinproject.aideo.setting

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import jinproject.aideo.core.TopLevelRoute
import jinproject.aideo.design.R
import jinproject.aideo.setting.setting.SettingScreen
import jinproject.aideo.setting.settingAiModel.SettingAiModelScreen
import jinproject.aideo.setting.subscription.SubscriptionScreen
import jinproject.aideo.setting.term.TermScreen
import kotlinx.serialization.Serializable

@Serializable
sealed class SettingRoute {
    @Serializable
    data object SettingGraph : SettingRoute()

    @Serializable
    data object Setting : SettingRoute(), TopLevelRoute {
        override val icon: Int = R.drawable.ic_more
        override val iconClicked: Int = R.drawable.ic_more
    }

    @Serializable
    data object SettingAiModel : SettingRoute()

    @Serializable
    data object Subscription : SettingRoute()

    @Serializable
    data object Term : SettingRoute()
}

fun NavGraphBuilder.settingNavigation(
    navigatePopBackStack: () -> Unit,
    navigateToSettingAIModel: () -> Unit,
    navigateToSubscription: () -> Unit,
    navigateToTerm: () -> Unit,
) {
    navigation<SettingRoute.SettingGraph>(
        startDestination = SettingRoute.Setting
    ) {
        composable<SettingRoute.Setting> {
            SettingScreen(
                navigateToSettingAIModel = navigateToSettingAIModel,
                navigateToSubscription = navigateToSubscription,
                navigateToTerm = navigateToTerm,
            )
        }

        composable<SettingRoute.SettingAiModel> {
            SettingAiModelScreen(
                navigatePopBackStack = navigatePopBackStack,
            )
        }

        composable<SettingRoute.Subscription> {
            SubscriptionScreen(
                navigatePopBackStack = navigatePopBackStack,
            )
        }

        composable<SettingRoute.Term> {
            TermScreen(
                navigatePopBackStack = navigatePopBackStack,
            )
        }
    }
}

fun NavController.navigateToSettingGraph(navOptions: NavOptions?) {
    navigate(SettingRoute.SettingGraph, navOptions)
}

fun NavController.navigateToSettingAIModel() {
    navigate(SettingRoute.SettingAiModel)
}

fun NavController.navigateToSubscription() {
    navigate(SettingRoute.Subscription)
}

fun NavController.navigateToTerm() {
    navigate(SettingRoute.Term)
}