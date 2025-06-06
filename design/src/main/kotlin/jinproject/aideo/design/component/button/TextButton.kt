package jinproject.aideo.design.component.button

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jinproject.aideo.design.utils.PreviewAideoTheme
import jinproject.aideo.design.theme.Typography
import jinproject.aideo.design.R

@Composable
fun TextButton(
    text: String,
    modifier: Modifier = Modifier,
    contentPaddingValues: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    shape: RoundedCornerShape = RoundedCornerShape(100.dp),
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DefaultButton(
        modifier = modifier,
        backgroundColor = MaterialTheme.colorScheme.primary,
        contentPaddingValues = contentPaddingValues,
        shape = shape,
        onClick = onClick,
        enabled = enabled,
    ) {
        Text(
            text = text,
            style = Typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
fun TextCombinedButton(
    content: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDoubleClick: () -> Unit = {},
) {
    DefaultCombinedButton(
        modifier = modifier,
        backgroundColor = MaterialTheme.colorScheme.primary,
        contentPaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        onClick = onClick,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
    ) {
        Text(
            text = content,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Preview()
@Composable
private fun PreviewDefaultButton() =
    PreviewAideoTheme {
        TextButton(
            text = "버튼",
            onClick = {}
        )
    }

@Preview()
@Composable
private fun PreviewSelectionButton() =
    PreviewAideoTheme {
        SelectionButton(
            buttonStatus = ButtonStatus.ON,
            modifier = Modifier
                .width(200.dp)
                .height(26.dp),
            textYes = "on",
            textNo = "off",
            onClickYes = {},
            onClickNo = {}
        )
    }
