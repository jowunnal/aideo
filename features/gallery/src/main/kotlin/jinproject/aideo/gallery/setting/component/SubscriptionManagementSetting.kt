package jinproject.aideo.gallery.setting.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jinproject.aideo.design.R
import jinproject.aideo.design.component.button.clickableAvoidingDuplication
import jinproject.aideo.design.component.text.DescriptionLargeText
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.theme.AideoColor
import jinproject.aideo.design.theme.AideoTheme

@Composable
internal fun SubscriptionManagementSetting(
    hasSubscription: Boolean,
    navigateToSubscriptionManagement: () -> Unit,
    navigateToSubscription: () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (hasSubscription)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.outline.copy(0.2f),
        label = "borderColor"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (hasSubscription)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else
            MaterialTheme.colorScheme.background,
        label = "backgroundColor"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .shadow(1.dp, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        DescriptionLargeText(text = stringResource(R.string.subscription_management_title))

        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .background(backgroundColor, RoundedCornerShape(10.dp))
                .drawBehind {
                    drawRect(
                        color = borderColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.cornerPathEffect(10.dp.toPx())
                        )
                    )
                }
                .clickableAvoidingDuplication {
                    if (hasSubscription)
                        navigateToSubscriptionManagement()
                    else
                        navigateToSubscription()
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DescriptionMediumText(
                text = stringResource(R.string.subscription_management_title),
            )

            if (hasSubscription) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_check_small),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(4.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSubscriptionManagementSettingNotSubscribed() = AideoTheme {
    SubscriptionManagementSetting(
        hasSubscription = false,
        navigateToSubscriptionManagement = {},
        navigateToSubscription = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewSubscriptionManagementSettingSubscribed() = AideoTheme {
    SubscriptionManagementSetting(
        hasSubscription = true,
        navigateToSubscriptionManagement = {},
        navigateToSubscription = {},
    )
}
