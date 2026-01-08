package jinproject.aideo.design.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jinproject.aideo.design.R
import jinproject.aideo.design.utils.PreviewAideoTheme

@Composable
fun DropDownMenuCustom(
    label: String? = null,
    selectedText: String,
    items: List<String>,
    modifier: Modifier = Modifier,
    @DrawableRes iconHeader: Int? = null,
    @DrawableRes iconTail: Int? = null,
    onClickItem: (String) -> Unit,
    onClickTailIcon: () -> Unit = {},
) {
    val dropDownExpandedState = remember {
        mutableStateOf(false)
    }
    Column(
        modifier = modifier
            .clickable {
                dropDownExpandedState.value = !dropDownExpandedState.value
            },
        verticalArrangement = Arrangement.Center,
    ) {
        label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        VerticalSpacer(height = 1.dp)
        Row(
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.scrim, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconHeader?.let {
                Icon(
                    painter = painterResource(id = iconHeader),
                    contentDescription = "DropDownMenuIconHeader"
                )
                HorizontalSpacer(width = 12.dp)
            }

            Text(
                text = selectedText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
            )

            iconTail?.let {
                HorizontalSpacer(8.dp)
                IconButton(
                    onClick = onClickTailIcon,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(id = iconTail),
                        contentDescription = "DropDownMenuIconTail",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        DropdownMenu(
            expanded = dropDownExpandedState.value,
            onDismissRequest = { dropDownExpandedState.value = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.background)
        ) {
            items.forEach {
                DropdownMenuItem(
                    text = {
                        Text(text = it)
                    },
                    onClick = {
                        onClickItem(it)
                        dropDownExpandedState.value = false
                    }
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun PreviewDropDownMenuCustom() {
    PreviewAideoTheme {
        DropDownMenuCustom(
            iconHeader = R.drawable.icon_home,
            iconTail = R.drawable.icon_alarm,
            label = "라벨텍스트",
            selectedText = "컨탠트 텍스트",
            items = listOf("컨텐트 텍스트"),
            onClickItem = {},
            onClickTailIcon = {},
        )
    }
}