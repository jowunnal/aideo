package jinproject.aideo.core.media.audio

import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class AudioProcessorTest {

    @Test
    fun `same sample rate keeps pcm length and converts to normalized floats`() {
        val pcm = shortArrayOf(Short.MIN_VALUE, 0, Short.MAX_VALUE)
        val bytes = pcm.toByteArray()

        val result = AudioProcessor.normalizeAudioSample(bytes, AudioConfig.SAMPLE_RATE)

        result.size shouldBe 3
        result[0] shouldBe (-1.0f plusOrMinus 0.0001f)
        result[1] shouldBe (0.0f plusOrMinus 0.0001f)
        result[2] shouldBe (0.9999695f plusOrMinus 0.0001f)
    }

    @Test
    fun `downsampling keeps low frequency tone near original frequency`() {
        val input =
            generateSineWave(sampleRate = 48_000, frequencyHz = 1_000, durationSeconds = 0.25)

        val result = AudioProcessor.linearResample(input, 48_000)
        val detected = detectDominantFrequency(result, AudioConfig.SAMPLE_RATE)

        assertTrue(
            detected in 950..1_050,
            "expected dominant frequency near 1000Hz but was $detected Hz"
        )
    }

    @Test
    fun `downsampling suppresses tone above destination nyquist to avoid aliasing`() {
        val input =
            generateSineWave(sampleRate = 48_000, frequencyHz = 12_000, durationSeconds = 0.25)

        val result = AudioProcessor.linearResample(input, 48_000)
        val trimmed = result.copyOfRange(128, result.size - 128)

        rms(trimmed) shouldBeLessThan 1_500.0
    }

    @Test
    fun `normalizeAudioSample 다운샘플링 시 저주파 톤 보존`() {
        val input =
            generateSineWave(sampleRate = 48_000, frequencyHz = 1_000, durationSeconds = 0.25)

        val result = AudioProcessor.normalizeAudioSample(input, 48_000)
        val asShort = result.toShortArray()
        val detected = detectDominantFrequency(asShort, AudioConfig.SAMPLE_RATE)

        assertTrue(
            detected in 950..1_050,
            "expected dominant frequency near 1000Hz but was $detected Hz"
        )
    }

    @Test
    fun `normalizeAudioSample 다운샘플링 시 Nyquist 위 주파수 차단`() {
        val input =
            generateSineWave(sampleRate = 48_000, frequencyHz = 12_000, durationSeconds = 0.25)

        val result = AudioProcessor.normalizeAudioSample(input, 48_000)
        val asShort = result.toShortArray()
        val trimmed = asShort.copyOfRange(128, asShort.size - 128)

        rms(trimmed) shouldBeLessThan 1_500.0
    }

    private fun FloatArray.toShortArray(): ShortArray =
        ShortArray(size) { (this[it] * 32768f).toInt().toShort() }

    private fun generateSineWave(
        sampleRate: Int,
        frequencyHz: Int,
        durationSeconds: Double,
        amplitude: Double = 24_000.0,
    ): ShortArray {
        val sampleCount = (sampleRate * durationSeconds).roundToInt()
        return ShortArray(sampleCount) { index ->
            (sin(2 * PI * frequencyHz * index / sampleRate) * amplitude).roundToInt().toShort()
        }
    }

    private fun detectDominantFrequency(
        samples: ShortArray,
        sampleRate: Int,
    ): Int {
        var bestFrequency = 0
        var bestMagnitude = Double.NEGATIVE_INFINITY
        for (frequency in 100 until sampleRate / 2 step 10) {
            var real = 0.0
            var imaginary = 0.0
            samples.forEachIndexed { index, sample ->
                val angle = 2 * PI * frequency * index / sampleRate
                real += sample * kotlin.math.cos(angle)
                imaginary -= sample * kotlin.math.sin(angle)
            }
            val magnitude = real * real + imaginary * imaginary
            if (magnitude > bestMagnitude) {
                bestMagnitude = magnitude
                bestFrequency = frequency
            }
        }
        return bestFrequency
    }

    private fun rms(samples: ShortArray): Double {
        val meanSquare = samples.map { sample -> sample.toDouble() * sample }.average()
        return sqrt(meanSquare)
    }

    private fun ShortArray.toByteArray(): ByteArray {
        val output = ByteArray(size * 2)
        forEachIndexed { index, value ->
            output[index * 2] = (value.toInt() and 0xFF).toByte()
            output[index * 2 + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
        }
        return output
    }
}
