package jinproject.aideo.core.media.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

object AudioProcessor {
    /**
     * 오디오 데이터(ByteArray) 를 전처리 하는 함수
     *
     * 목적) Sample Rate - 16000, PCM-Float 형식으로 오디오 데이터를 변환
     */
    fun normalizeAudioSample(
        audioChunk: ByteArray,
        sampleRate: Int,
    ): FloatArray {
        val shortBuffer =
            ByteBuffer.wrap(audioChunk).order(ByteOrder.nativeOrder()).asShortBuffer()
        val shortArray = ShortArray(shortBuffer.limit())
        shortBuffer.get(shortArray)

        val reSampledShortArray = if (sampleRate != AudioConfig.SAMPLE_RATE) {
            linearResample(shortArray, sampleRate)
        } else {
            shortArray
        }

        return convertToFloatArray(reSampledShortArray)
    }

    /**
     * 선형 보간법을 이용하여 Sample Rate 를 16000 으로 변환하는 함수
     */
    fun linearResample(input: ShortArray, srcRate: Int): ShortArray {
        if (input.isEmpty()) return input

        val processedInput =
            if (srcRate > AudioConfig.SAMPLE_RATE) {
                applyAntiAliasingFilter(input, srcRate)
            } else {
                input
            }

        val ratio = AudioConfig.SAMPLE_RATE.toDouble() / srcRate
        val outputLength = (processedInput.size * ratio).roundToInt()
        val output = ShortArray(outputLength)
        for (i in output.indices) {
            val srcIndex = i / ratio
            val idx = srcIndex.toInt()
            val frac = srcIndex - idx
            val sample1 = processedInput.getOrElse(idx) { 0 }
            val sample2 = processedInput.getOrElse(idx + 1) { 0 }
            output[i] = ((sample1 * (1 - frac) + sample2 * frac)).toInt().toShort()
        }
        return output
    }

    private fun applyAntiAliasingFilter(input: ShortArray, srcRate: Int): ShortArray {
        val taps = 127
        val halfTaps = taps / 2
        val cutoffHz = AudioConfig.SAMPLE_RATE * 0.45
        val normalizedCutoff = cutoffHz / srcRate
        val kernel = createLowPassKernel(taps, normalizedCutoff)

        return ShortArray(input.size) { sampleIndex ->
            var acc = 0.0
            for (tapIndex in kernel.indices) {
                val inputIndex = sampleIndex + tapIndex - halfTaps
                if (inputIndex in input.indices) {
                    acc += input[inputIndex] * kernel[tapIndex]
                }
            }
            acc.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun createLowPassKernel(taps: Int, normalizedCutoff: Double): DoubleArray {
        val kernel = DoubleArray(taps)
        val center = taps / 2
        var sum = 0.0

        for (index in 0 until taps) {
            val n = index - center
            val sinc =
                if (n == 0) {
                    2 * normalizedCutoff
                } else {
                    sin(2 * PI * normalizedCutoff * n) / (PI * n)
                }
            val window = 0.54 - 0.46 * kotlin.math.cos(2 * PI * index / (taps - 1))
            val coefficient = sinc * window
            kernel[index] = coefficient
            sum += coefficient
        }

        return DoubleArray(taps) { index -> kernel[index] / sum }
    }

    /**
     * ShortArray 를 FloatArray 로 변환하는 함수
     */
    fun convertToFloatArray(shortArray: ShortArray): FloatArray {
        return FloatArray(shortArray.size) { i ->
            shortArray[i] / 32768.0f
        }
    }
}
