package jinproject.aideo.core.utils

import jinproject.aideo.core.inference.whisper.AudioConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

object AudioProcessor {
    /**
     * 오디오 데이터(ByteArray) 를 전처리 하는 함수
     *
     * 목적) Sample Rate - 16000, Channel - 1(모노), PCM-Float 형식으로 오디오 데이터를 변환
     */
    fun normalizeAudioSample(
        audioChunk: ByteArray,
        sampleRate: Int,
        channelCount: Int,
    ): FloatArray {
        val shortBuffer =
            ByteBuffer.wrap(audioChunk).order(ByteOrder.nativeOrder()).asShortBuffer()
        val shortArray = ShortArray(shortBuffer.limit())
        shortBuffer.get(shortArray)

        /*val monoShortArray = if (channelCount == 2) {
            ShortArray(shortArray.size / 2) { i ->
                (((shortArray[2 * i].toInt() + shortArray[2 * i + 1].toInt()) / 2).toShort())
            }
        } else {
            shortArray
        }*/

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
        val ratio = AudioConfig.SAMPLE_RATE.toDouble() / srcRate
        val outputLength = (input.size * ratio).roundToInt()
        val output = ShortArray(outputLength)
        for (i in output.indices) {
            val srcIndex = i / ratio
            val idx = srcIndex.toInt()
            val frac = srcIndex - idx
            val sample1 = input.getOrElse(idx) { 0 }
            val sample2 = input.getOrElse(idx + 1) { 0 }
            output[i] = ((sample1 * (1 - frac) + sample2 * frac)).toInt().toShort()
        }
        return output
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