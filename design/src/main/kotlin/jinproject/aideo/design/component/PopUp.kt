package jinproject.aideo.design.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

@Stable
class PopUpInfo {
    var visibility: Boolean by mutableStateOf(false)
        private set
    var offsetInWindow: IntOffset by mutableStateOf(IntOffset.Zero)
        private set

    fun updateVisibility(visible: Boolean) {
        visibility = visible
    }

    fun updateOffset(offset: IntOffset) {
        this.offsetInWindow = offset
    }
}

@Composable
fun PopUp(
    popUpInfo: PopUpInfo,
    content: @Composable () -> Unit,
) {
    if (popUpInfo.visibility)
        Popup(
            popupPositionProvider = remember {
                object: PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        return popUpInfo.offsetInWindow
                    }
                }
            },
            onDismissRequest = {
                popUpInfo.updateVisibility(false)
            },
            properties = PopupProperties()
        ) {
            content()
        }
}