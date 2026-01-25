package jinproject.aideo.design.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup

data class PopUpInfo(val offset: IntOffset) {
    var visibility: Boolean by mutableStateOf(false)
        private set

    fun changeVisibility(visible: Boolean) {
        visibility = visible
    }
}

@Composable
fun PopUp(
    popUpInfo: PopUpInfo,
    content: @Composable () -> Unit,
) {
    if (popUpInfo.visibility)
        Popup(
            offset = popUpInfo.offset,
            onDismissRequest = {
                popUpInfo.changeVisibility(false)
            }
        ) {
            content()
        }
}