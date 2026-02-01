package jinproject.aideo.setting.setting.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jinproject.aideo.design.R
import jinproject.aideo.design.component.HorizontalSpacer
import jinproject.aideo.design.component.button.clickableAvoidingDuplication
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.component.text.DescriptionSmallText
import jinproject.aideo.design.theme.AideoTheme

@Composable
internal fun SettingMenuItem(
    @DrawableRes icon: Int,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val iconBackgroundColor = if (highlight) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
    }

    val iconTintColor = if (highlight) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    val labelColor = if (highlight) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickableAvoidingDuplication(onClick = onClick)
            .padding(
                horizontal = 16.dp,
                vertical = 20.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            imageVector = ImageVector.vectorResource(icon),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = iconBackgroundColor,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(8.dp),
            colorFilter = ColorFilter.tint(iconTintColor)
        )

        HorizontalSpacer(16.dp)

        SettingMenuItemText(
            label = label,
            description = description,
            labelColor = labelColor,
            modifier = Modifier.weight(1f),
        )

        HorizontalSpacer(8.dp)

        Image(
            imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_right_small),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
        )
    }
}

@Composable
private fun SettingMenuItemText(
    label: String,
    description: String,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DescriptionMediumText(
            text = label,
            color = labelColor,
        )
        DescriptionSmallText(
            text = description,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSettingMenuItem() = AideoTheme {
    SettingMenuItem(
        icon = R.drawable.ic_crown,
        label = "Manage Subscription",
        description = "View and upgrade your plan",
        onClick = {},
        highlight = false,
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewSettingMenuItemHighlight() = AideoTheme {
    SettingMenuItem(
        icon = R.drawable.ic_crown,
        label = "Manage Subscription",
        description = "View and upgrade your plan",
        onClick = {},
        highlight = true,
    )
}