package jinproject.aideo.design.component.layout.hideable

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

@Composable
fun Modifier.topBarHidingScroll(state: SystemBarHidingState) =
    this.nestedScroll(
        SystemBarHidingNestedScroll(
            hide = state::hideOrAppear,
        )
    )

@Composable
fun Modifier.navigationBarHidingScroll(state: SystemBarHidingState) =
    this.nestedScroll(
        SystemBarHidingNestedScroll(
            hide = state::hideOrAppear,
        )
    )

private class SystemBarHidingNestedScroll(
    private val hide: (Float) -> Float,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        return when {
            source == NestedScrollSource.Drag && available.y < 0f -> Offset(0f, hide(available.y))
            else -> Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return when {
            source == NestedScrollSource.Drag && available.y > 0f -> Offset(0f, hide(available.y))
            else -> Offset.Zero
        }
    }
}