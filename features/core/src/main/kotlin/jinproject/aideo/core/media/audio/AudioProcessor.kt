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

        return normalizeAudioSample(shortArray, sampleRate)
    }

    /**
     * 16-bit PCM ShortArray 를 16kHz Float PCM 으로 정규화.
     *
     * 다운샘플링 (srcRate > 16kHz) 핫 경로:
     *   - 출력 위치에서 필요한 idx, idx+1 두 곳에만 127-tap FIR 적용 (lazy)
     *   - 보간 + Float 정규화를 한 패스로 결합 → 중간 ShortArray 두 개 제거
     *   - FIR 커널을 srcRate 기준 캐싱 → chunk 마다 sin/cos 127회 호출 제거
     */
    fun normalizeAudioSample(
        audioChunk: ShortArray,
        sampleRate: Int,
    ): FloatArray {
        if (audioChunk.isEmpty()) return FloatArray(0)
        if (sampleRate == AudioConfig.SAMPLE_RATE) {
            return convertToFloatArray(audioChunk)
        }
        if (sampleRate < AudioConfig.SAMPLE_RATE) {
            return convertToFloatArray(linearResample(audioChunk, sampleRate))
        }

        val kernel = cachedLowPassKernel(sampleRate)
        val ratio = AudioConfig.SAMPLE_RATE.toDouble() / sampleRate
        val outputLength = (audioChunk.size * ratio).roundToInt()
        val output = FloatArray(outputLength)

        for (i in output.indices) {
            val srcIndex = i / ratio
            val idx = srcIndex.toInt()
            val frac = srcIndex - idx

            val sample1 = firAt(idx, audioChunk, kernel)
            val sample2 = firAt(idx + 1, audioChunk, kernel)

            val resampled = ((sample1 * (1 - frac) + sample2 * frac)).toInt().toShort()
            output[i] = resampled / 32768.0f
        }
        return output
    }

    fun normalizeAudioSample(
        audioChunk: FloatArray,
        sampleRate: Int,
    ): FloatArray {
        return if (sampleRate != AudioConfig.SAMPLE_RATE) {
            linearResampleFloat(audioChunk, sampleRate)
        } else {
            audioChunk
        }
    }

    /**
     * ENCODING_PCM_8BIT 는 unsigned 0~255 형식이므로 별도 처리.
     */
    fun normalizeAudioSample8Bit(
        audioChunk: ByteArray,
        sampleRate: Int,
    ): FloatArray {
        val shortArray = ShortArray(audioChunk.size) { index ->
            (((audioChunk[index].toInt() and 0xFF) - 128) shl 8).toShort()
        }
        return normalizeAudioSample(shortArray, sampleRate)
    }

    private fun linearResampleFloat(input: FloatArray, srcRate: Int): FloatArray {
        if (input.isEmpty()) return input

        val ratio = AudioConfig.SAMPLE_RATE.toDouble() / srcRate
        val outputLength = (input.size * ratio).roundToInt()
        val output = FloatArray(outputLength)
        for (i in output.indices) {
            val srcIndex = i / ratio
            val idx = srcIndex.toInt()
            val frac = srcIndex - idx
            val sample1 = input.getOrElse(idx) { 0f }
            val sample2 = input.getOrElse(idx + 1) { 0f }
            output[i] = (sample1 * (1 - frac) + sample2 * frac).toFloat()
        }
        return output
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

    private val kernelCache = HashMap<Int, DoubleArray>()

    private fun cachedLowPassKernel(srcRate: Int): DoubleArray {
        return kernelCache.getOrPut(srcRate) {
            val taps = 127
            val cutoffHz = AudioConfig.SAMPLE_RATE * 0.45
            val normalizedCutoff = cutoffHz / srcRate
            createLowPassKernel(taps, normalizedCutoff)
        }
    }

    private fun firAt(
        centerIndex: Int,
        input: ShortArray,
        kernel: DoubleArray,
    ): Short {
        val inputSize = input.size
        if (centerIndex !in 0..<inputSize) return 0
        val halfTaps = kernel.size / 2
        var acc = 0.0
        for (tapIndex in kernel.indices) {
            val inputIndex = centerIndex + tapIndex - halfTaps
            if (inputIndex in 0 until inputSize) {
                acc += input[inputIndex] * kernel[tapIndex]
            }
        }
        return acc.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}
