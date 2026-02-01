package jinproject.aideo.setting.setting.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jinproject.aideo.design.R
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.theme.AideoTheme

@Composable
internal fun ColumnScope.SettingSection(
    title: String,
    content: @Composable () -> Unit,
) {
    DescriptionMediumText(
        text = title.uppercase(),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        content()
    }
}

@Composable
internal fun SettingMenuDivider(
    modifier: Modifier = Modifier,
) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewSettingSection() = AideoTheme {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
    ) {
        SettingSection(
            title = "Subscription",
        ) {
            SettingMenuItem(
                icon = R.drawable.ic_crown,
                label = "Manage Subscription",
                description = "View and upgrade your plan",
                onClick = {},
                highlight = true,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSettingSectionMultipleItems() = AideoTheme {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
    ) {
        SettingSection(
            title = "Support",
        ) {
            SettingMenuItem(
                icon = R.drawable.ic_help,
                label = "Help Center",
                description = "Get help with your account",
                onClick = {},
            )
            SettingMenuDivider()
            SettingMenuItem(
                icon = R.drawable.ic_help,
                label = "Terms of Service",
                description = "View terms and conditions",
                onClick = {},
            )
        }
    }
}