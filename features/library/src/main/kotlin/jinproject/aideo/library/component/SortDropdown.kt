package jinproject.aideo.library.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jinproject.aideo.design.R
import jinproject.aideo.design.component.button.clickableAvoidingDuplication
import jinproject.aideo.design.component.text.DescriptionMediumText
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.library.model.SortOption

@Composable
internal fun SortDropdown(
    selectedOption: SortOption,
    onOptionSelected: (SortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickableAvoidingDuplication { expanded = true }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DescriptionMediumText(
            text = stringResource(selectedOption.labelResId),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_down_outlined),
            contentDescription = null,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        ) {
            SortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        DescriptionMediumText(
                            text = stringResource(option.labelResId),
                            color = if (option == selectedOption)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview
@Composable
private fun SortDropdownPreview() {
    PreviewAideoTheme {
        SortDropdown(
            selectedOption = SortOption.NEWEST,
            onOptionSelected = {},
        )
    }
}
