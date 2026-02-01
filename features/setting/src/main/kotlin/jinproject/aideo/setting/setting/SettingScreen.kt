package jinproject.aideo.setting.setting

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jinproject.aideo.design.R
import jinproject.aideo.design.component.VerticalSpacer
import jinproject.aideo.design.component.bar.DefaultTitleAppBar
import jinproject.aideo.design.component.layout.DefaultLayout
import jinproject.aideo.design.component.paddingvalues.AideoPaddingValues
import jinproject.aideo.design.theme.AideoTheme
import jinproject.aideo.setting.setting.component.SettingMenuItem
import jinproject.aideo.setting.setting.component.SettingSection

@Composable
internal fun SettingScreen(
    navigateToSettingAIModel: () -> Unit,
    navigateToSubscription: () -> Unit,
    navigateToTerm: () -> Unit,
) {
    val backgroundColor = MaterialTheme.colorScheme.primary

    DefaultLayout(
        topBar = {
            DefaultTitleAppBar(
                title = stringResource(R.string.setting_title),
                backgroundColor = backgroundColor,
                contentColor = contentColorFor(backgroundColor),
            )
        },
        contentPaddingValues = AideoPaddingValues(vertical = 24.dp, horizontal = 16.dp),
    ) {
        SettingSection(
            title = stringResource(R.string.setting_section_ai_model),
        ) {
            SettingMenuItem(
                icon = R.drawable.ic_sparkles,
                label = stringResource(R.string.setting_ai_models_languages),
                description = stringResource(R.string.setting_ai_models_languages_desc),
                onClick = navigateToSettingAIModel,
            )
        }

        VerticalSpacer(24.dp)

        SettingSection(
            title = stringResource(R.string.setting_section_subscription),
        ) {
            SettingMenuItem(
                icon = R.drawable.ic_crown,
                label = stringResource(R.string.setting_manage_subscription),
                description = stringResource(R.string.setting_manage_subscription_desc),
                onClick = navigateToSubscription,
                highlight = true,
            )
        }

        VerticalSpacer(24.dp)

        SettingSection(
            title = stringResource(R.string.setting_section_support),
        ) {
            SettingMenuItem(
                icon = R.drawable.ic_help,
                label = stringResource(R.string.term_title),
                description = stringResource(R.string.setting_term_desc),
                onClick = navigateToTerm,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSettingScreen() = AideoTheme {
    SettingScreen(
        navigateToSettingAIModel = {},
        navigateToSubscription = {},
        navigateToTerm = {},
    )
}