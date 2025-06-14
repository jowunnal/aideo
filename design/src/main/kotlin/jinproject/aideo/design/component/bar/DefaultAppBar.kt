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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jinproject.aideo.design.component.text.SearchTextField
import jinproject.aideo.design.R
import jinproject.aideo.design.component.HorizontalWeightSpacer
import jinproject.aideo.design.component.button.DefaultIconButton
import jinproject.aideo.design.component.text.AppBarText
import jinproject.aideo.design.utils.PreviewAideoTheme

@Composable
fun OneButtonAppBar(
    buttonAlignment: Alignment = Alignment.CenterStart,
    @DrawableRes icon: Int,
    onBackClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    DefaultAppBar(
        content = {
            DefaultIconButton(
                modifier = Modifier
                    .align(buttonAlignment),
                icon = icon,
                onClick = onBackClick,
                iconTint = MaterialTheme.colorScheme.onSurface,
                interactionSource = remember { MutableInteractionSource() }
            )
            content()
        }
    )
}

@Composable
fun BackButtonTitleAppBar(
    title: String,
    onBackClick: () -> Unit,
) {
    DefaultAppBar(
        content = {
            DefaultIconButton(
                modifier = Modifier
                    .align(Alignment.CenterStart),
                icon = R.drawable.ic_arrow_left,
                onClick = onBackClick,
                iconTint = MaterialTheme.colorScheme.onSurface,
                interactionSource = remember { MutableInteractionSource() }
            )
            AppBarText(text = title, modifier = Modifier.align(Alignment.Center))
        }
    )
}

@Composable
fun DefaultAppBar(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RectangleShape, clip = false)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        content()
    }
}

@Composable
fun DefaultRowScopeAppBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RectangleShape, clip = false)
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
fun BackButtonRowScopeAppBar(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit,
    content: @Composable RowScope.() -> Unit = {},
) {
    DefaultRowScopeAppBar(modifier = modifier) {
        DefaultIconButton(
            modifier = Modifier,
            icon = R.drawable.ic_arrow_left,
            onClick = onBackClick,
            iconTint = MaterialTheme.colorScheme.onSurface,
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