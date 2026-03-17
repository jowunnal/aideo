package jinproject.aideo.core.inference

import kotlinx.coroutines.flow.StateFlow

interface ProgressReportable {
    val progress: StateFlow<Float>
}