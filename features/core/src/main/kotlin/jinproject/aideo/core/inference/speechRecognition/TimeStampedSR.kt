package jinproject.aideo.core.inference.speechRecognition

import jinproject.aideo.data.TranslationManager

interface TimeStampedSR {
    val timeInfo: TimeInfo

    fun getBeginTimeOfSRT(): String =
        TranslationManager.formatSrtTime(timeInfo.standardTime + timeInfo.startTime)

    fun getEndTimeOfSRT(): String =
        TranslationManager.formatSrtTime(timeInfo.standardTime + timeInfo.endTime)

    fun setStandardTime(time: Float) {
        timeInfo.standardTime = time
    }

    fun setTimes(start: Float, end: Float) {
        timeInfo.apply {
            startTime = start
            endTime = end
        }
    }

    data class TimeInfo(
        var idx: Int,
        var startTime: Float,
        var endTime: Float,
        var standardTime: Float
    ) {
        companion object {
            fun getDefault(): TimeInfo = TimeInfo(
                idx = 0,
                startTime = 0f,
                endTime = 0f,
                standardTime = 0f
            )
        }
    }
}