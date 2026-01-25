package jinproject.aideo.design.component.bar

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jinproject.aideo.design.R
import jinproject.aideo.design.component.HorizontalWeightSpacer
import jinproject.aideo.design.component.button.DefaultIconButton
import jinproject.aideo.design.component.text.AppBarText
import jinproject.aideo.design.component.text.SearchTextField
import jinproject.aideo.design.utils.PreviewAideoTheme

@Composable
fun OneButtonTitleAppBar(
    buttonAlignment: Alignment = Alignment.CenterStart,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    title: String,
) {
    DefaultAppBar(
        content = {
            AppBarText(text = title, modifier = Modifier.align(Alignment.Center))
            DefaultIconButton(
                modifier = Modifier
                    .align(buttonAlignment),
                icon = icon,
                onClick = onClick,
                iconTint = MaterialTheme.colorScheme.onSurface,
                interactionSource = remember { MutableInteractionSource() }
            )
        }
    )
}

@Composable
fun RowScopedTitleAppBar(
    title: String,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(MaterialTheme.colorScheme.surface),
    content: @Composable RowScope.() -> Unit,
) {
    DefaultAppBar(
        backgroundColor = backgroundColor,
    ) {
        AppBarText(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            color = contentColor,
        )
        Row(
            modifier = Modifier
            .align(Alignment.CenterEnd)
            .background(backgroundColor)
        ) {
            content()
        }
    }
}

@Composable
fun BackButtonTitleAppBar(
    title: String,
    onBackClick: () -> Unit,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(MaterialTheme.colorScheme.surface),
) {
    DefaultAppBar(
        backgroundColor = backgroundColor,
        content = {
            DefaultIconButton(
                modifier = Modifier
                    .align(Alignment.CenterStart),
                icon = R.drawable.ic_arrow_left,
                onClick = onBackClick,
                interactionSource = remember { MutableInteractionSource() },
                backgroundTint = backgroundColor,
                iconTint = contentColor,
            )
            AppBarText(
                text = title,
                modifier = Modifier.align(Alignment.Center),
                color = contentColor,
            )
        }
    )
}

@Composable
fun DefaultAppBar(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RectangleShape, clip = false)
            .background(backgroundColor),
    ) {
        content()
    }
}

@Composable
fun DefaultRowScopeAppBar(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RectangleShape, clip = false)
            .background(backgroundColor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
fun BackButtonRowScopeAppBar(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    onBackClick: () -> Unit,
    content: @Composable RowScope.() -> Unit = {},
) {
    DefaultRowScopeAppBar(
        modifier = modifier,
        backgroundColor = backgroundColor,
    ) {
        DefaultIconButton(
            modifier = Modifier,
            icon = R.drawable.ic_arrow_left,
            onClick = onBackClick,
            iconTint = contentColorFor(backgroundColor),
            backgroundTint = backgroundColor,
            interactionSource = remember { MutableInteractionSource() }
        )
        content()
    }
}

@Composable
fun BackButtonSearchAppBar(
    modifier: Modifier = Modifier,
    textFieldState: TextFieldState,
    onBackClick: () -> Unit,
) {
    BackButtonRowScopeAppBar(
        onBackClick = onBackClick,
    ) {
        HorizontalWeightSpacer(float = 1f)
        SearchTextField(
            modifier = modifier
                .padding(top = 8.dp, bottom = 8.dp)
                .shadow(7.dp, RoundedCornerShape(10.dp)),
            textFieldState = textFieldState,
        )
    }
}

@Preview
@Composable
private fun PreviewBackButtonRowScopeAppBar() =
    PreviewAideoTheme {
        BackButtonRowScopeAppBar(
            onBackClick = {},
        ) {

        }
    }

@Preview
@Composable
private fun PreviewBackButtonTitleAppBar() =
    PreviewAideoTheme {
        BackButtonTitleAppBar(
            title = "앱바 타이틀",
            onBackClick = {},
        )
    }

@Preview
@Composable
private fun PreviewBackButtonSearchAppBar() =
    PreviewAideoTheme {
        val textFiledState = rememberTextFieldState()
        BackButtonSearchAppBar(
            textFieldState = textFiledState,
            onBackClick = {},
        )
    }