package jinproject.aideo.core

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundObserver @Inject constructor(): LifecycleEventObserver {
    @Volatile
    var isForeground: Boolean = false

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        isForeground = source.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
}