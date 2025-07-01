package jinproject.aideo.design.component.layout.hideable

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun rememberSystemBarHidingState(
    bar: SystemBarHidingState.Bar,
    scope: CoroutineScope = rememberCoroutineScope(),
): SystemBarHidingState {

    val state = remember {
        SystemBarHidingState(bar, scope)
    }

    return state
}

@Stable
class SystemBarHidingState(
    val bar: Bar,
    private val scope: CoroutineScope,
) {
    /**
     * 범위 : -(시스템바 높이) ~ 0f
     */
    var offset: Float by mutableFloatStateOf(0f)
        private set

    private val reducibleHeight by mutableIntStateOf(bar.maxHeight - bar.minHeight)

    private val systemBarHeight
        get() = when (bar) {
            is Bar.TOPBAR -> -reducibleHeight
            is Bar.NAVIGATIONBAR -> reducibleHeight
        }

    private val adjustedOffset by derivedStateOf {
        (when (bar) {
            is Bar.TOPBAR -> -offset
            is Bar.NAVIGATIONBAR -> offset
        } / reducibleHeight)
    }

    val progress get() = adjustedOffset.coerceIn(0f, 1f)

    fun hideOrAppear(delta: Float): Float {
        val newOffset = when (bar) {
            is Bar.NAVIGATIONBAR -> (offset - delta).coerceIn(0f, systemBarHeight.toFloat())
            is Bar.TOPBAR -> (offset + delta).coerceIn(systemBarHeight.toFloat(), 0f)
        }

        val consumed = newOffset - offset
        offset = newOffset

        return consumed
    }

    private val mutatorMutex = MutatorMutex()

    fun animateHideOrAppear() = scope.launch {
        mutatorMutex.mutate {
            animate(
                initialValue = offset,
                targetValue = if (offset == 0f) systemBarHeight.toFloat() else 0f,
                animationSpec = tween(250, easing = LinearEasing)
            ) { value, _ ->
                offset = value
            }
        }
    }

    sealed interface Bar {
        val maxHeight: Int
        val minHeight: Int

        data class TOPBAR(override val maxHeight: Int, override val minHeight: Int) : Bar
        data class NAVIGATIONBAR(override val maxHeight: Int, override val minHeight: Int) : Bar
    }
}