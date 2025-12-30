package jinproject.aideo.core.inference.whisper

import android.util.Log
import be.tarsos.dsp.resample.Resampler
import jinproject.aideo.core.audio.ExtractedAudioInfo
import jinproject.aideo.core.audio.MediaInfo
import jinproject.aideo.core.lite.VocabUtils
import jinproject.aideo.core.utils.AudioProcessor.normalizeAudioSample
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 오디오 세그먼트 데이터 클래스
 */
private data class AudioSegment(
    val samples: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioSegment

        return samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        return samples.contentHashCode()
    }
}

/**
 * Whisper 모델의 입출력 구조에 맞게 전&후 처리를 담당
 */
class WhisperAudioProcessor @Inject constructor(
    private val localFileDataSource: LocalFileDataSource,
) {
    private lateinit var samplingAudioList: ByteArray
    var lastProducedAudioIndex = 0
    var counter = 0

    /**
     * 오디오 데이터를 전처리하여 소비자 코루틴에게 전송하는 함수
     *
     * 1. 오디오를 30초 * 16kHz(sampling rate) * 1(모노채널) 분량이 될 때 까지 배열에 축적
     * 2. 30초 분량 이상이 모이면, 소비자 코루틴이 이후 과정을 수행할 수 있도록 채널에 전송
     * 3. 전송 후, 할당된 메모리를 재 사용하기 위해 기존 배열을 기본값(0)으로 초기화
     *
     * @param audioData 오디오 데이터
     */
    @OptIn(ExperimentalAtomicApi::class, ExperimentalCoroutinesApi::class)
    suspend fun preProcessing(
        extractedAudioInfo: ExtractedAudioInfo,
        inferenceAudioChannel: SendChannel<FloatArray>,
        maxLength: Int = 30,
    ) {
        runCatching {
            val stepSize = maxLength * extractedAudioInfo.mediaInfo.sampleRate * Float.SIZE_BYTES

            if (::samplingAudioList.isInitialized.not())
                samplingAudioList = ByteArray(stepSize)
/*
            if(extractedAudioInfo.audioSize > stepSize) {
                val normalized = normalizeAudioSample(
                    audioChunk = ByteArray(extractedAudioInfo.audioSize) {
                        extractedAudioInfo.audioData[it]
                    },
                    sampleRate = extractedAudioInfo.mediaInfo.sampleRate,
                    channelCount = extractedAudioInfo.mediaInfo.channelCount
                )

                inferenceAudioChannel.send(normalized)
            }
            else if (lastProducedAudioIndex + extractedAudioInfo.audioSize > stepSize) {
                Log.d("test", "전송전 데이터양: ${samplingAudioList.size}")

                clearSampledAudio(
                    inferenceAudioChannel = inferenceAudioChannel,
                    mediaFormat = extractedAudioInfo.mediaInfo
                )
            }

            repeat(extractedAudioInfo.audioSize) { idx ->
                samplingAudioList[lastProducedAudioIndex++] = extractedAudioInfo.audioData[idx]
            }*/
        }.onFailure { t ->
            Log.d("test", "error occurred ${t.message}")
        }
    }

    suspend fun clearSampledAudio(
        inferenceAudioChannel: SendChannel<FloatArray>,
        mediaFormat: MediaInfo,
    ) {
        val normalizedAudio = normalizeAudioSample(
            audioChunk = samplingAudioList,
            sampleRate = mediaFormat.sampleRate,
            channelCount = mediaFormat.channelCount
        )

        saveFloatArrayAsWav(
            normalizedAudio
        )

        inferenceAudioChannel.send(normalizedAudio)

        Log.d(
            "test",
            "전송된 데이터 양: ${normalizedAudio.size} , 증감: ${normalizedAudio.size - samplingAudioList.size}"
        )

        repeat(samplingAudioList.size) { idx ->
            samplingAudioList[idx] = 0x00.toByte()
        }
        lastProducedAudioIndex = 0
        Log.d("test", "남겨진 데이터 전송 완료")
    }

    /**
     * Test 용도로 음성 데이터(FloatArray)를 오디오 파일로 저장하는 함수
     */
    fun saveFloatArrayAsWav(
        floatArray: FloatArray,
        sampleRate: Int = 16000,
        fileName: String = "whisper_audio.wav"
    ) {
        try {
            // Float를 16-bit PCM으로 변환
            val shortArray = ShortArray(floatArray.size) { i ->
                (floatArray[i] * Short.MAX_VALUE).toInt().toShort()
            }

            localFileDataSource.createFileAndWriteOnOutputStream(fileName) { outputStream ->
                // WAV 헤더 작성
                writeWavHeader(outputStream, shortArray.size, sampleRate)

                // PCM 데이터 작성
                val byteBuffer = ByteBuffer.allocate(shortArray.size * 2)
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN)

                for (sample in shortArray) {
                    byteBuffer.putShort(sample)
                }

                outputStream.write(byteBuffer.array())
                true
            }

            counter++

            Log.d("AudioSaver", "WAV 파일 저장 완료: $fileName")
        } catch (e: Exception) {
            Log.e("AudioSaver", "WAV 파일 저장 실패: ${e.message}")
        }
    }

    private fun writeWavHeader(outputStream: OutputStream, dataSize: Int, sampleRate: Int) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF 헤더
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize * 2) // 파일 크기 - 8
        header.put("WAVE".toByteArray())

        // fmt 청크
        header.put("fmt ".toByteArray())
        header.putInt(16) // fmt 청크 크기
        header.putShort(1) // PCM 포맷
        header.putShort(1) // 모노 채널
        header.putInt(sampleRate) // 샘플 레이트
        header.putInt(sampleRate * 2) // 바이트 레이트
        header.putShort(2) // 블록 정렬
        header.putShort(16) // 비트 깊이

        // data 청크
        header.put("data".toByteArray())
        header.putInt(dataSize * 2) // 데이터 크기

        outputStream.write(header.array())
    }

}

private const val SINC_FILTER_SIZE = 64  // 필터 크기 (더 크면 품질 향상)
private const val SINC_ZERO_CROSSINGS = 8  // Zero crossing 수

// Pre-emphasis 필터 (음성 인식 정확도 향상)
private const val PRE_EMPHASIS_COEF = 0.97f

/**
 * 오디오 리샘플러 클래스 (간단한 버전)
 * PCM 오디오 데이터의 sample rate와 channel count를 변환합니다.
 */
class AudioResampler {

    /**
     * 오디오 데이터를 리샘플링합니다 (FloatArray 입력)
     *
     * @param inputData 입력 Float 배열 (-1.0 ~ 1.0 범위)
     * @param inputSampleRate 입력 샘플 레이트 (Hz)
     * @param inputChannels 입력 채널 수
     * @param outputSampleRate 출력 샘플 레이트 (Hz)
     * @param outputChannels 출력 채널 수
     * @return 리샘플링된 Float 배열 (-1.0 ~ 1.0 범위)
     */
    fun resample(
        inputData: FloatArray,
        inputSampleRate: Int,
        inputChannels: Int,
        outputSampleRate: Int,
        outputChannels: Int
    ): FloatArray {
        var samples = inputData

        // 1단계: 채널 변환
        /*if (inputChannels != outputChannels) {
            samples = convertChannels(samples, inputChannels, outputChannels)
        }*/

        // 2단계: 샘플 레이트 변환
        if (inputSampleRate != outputSampleRate) {
            samples = resampleRate(samples, inputSampleRate, outputSampleRate)
        }

        return samples
    }

    /**
     * 오디오 데이터를 리샘플링합니다 (ByteArray 입력)
     *
     * @param inputData 입력 PCM 데이터 (16-bit little-endian)
     * @param inputSampleRate 입력 샘플 레이트 (Hz)
     * @param inputChannels 입력 채널 수
     * @param outputSampleRate 출력 샘플 레이트 (Hz)
     * @param outputChannels 출력 채널 수
     * @return 리샘플링된 Float 배열 (-1.0 ~ 1.0 범위)
     */
    fun resample(
        inputData: ByteArray,
        inputSampleRate: Int,
        inputChannels: Int,
        outputSampleRate: Int,
        outputChannels: Int
    ): FloatArray {
        val floatSamples = bytesToFloatSamples(inputData, inputChannels)
        return resample(floatSamples, inputSampleRate, inputChannels, outputSampleRate, outputChannels)
    }

    /**
     * 오디오 데이터를 리샘플링합니다 (ByteArray 반환)
     *
     * @param inputData 입력 PCM 데이터 (16-bit little-endian)
     * @param inputSampleRate 입력 샘플 레이트 (Hz)
     * @param inputChannels 입력 채널 수
     * @param outputSampleRate 출력 샘플 레이트 (Hz)
     * @param outputChannels 출력 채널 수
     * @return 리샘플링된 PCM 데이터 (16-bit little-endian)
     */
    fun resampleToBytes(
        inputData: ByteArray,
        inputSampleRate: Int,
        inputChannels: Int,
        outputSampleRate: Int,
        outputChannels: Int
    ): ByteArray {
        val samples = resample(
            inputData,
            inputSampleRate,
            inputChannels,
            outputSampleRate,
            outputChannels
        )

        return floatSamplesToBytes(samples)
    }

    /**
     * 오디오 데이터를 리샘플링합니다 (FloatArray -> ByteArray)
     *
     * @param inputData 입력 Float 배열 (-1.0 ~ 1.0 범위)
     * @param inputSampleRate 입력 샘플 레이트 (Hz)
     * @param inputChannels 입력 채널 수
     * @param outputSampleRate 출력 샘플 레이트 (Hz)
     * @param outputChannels 출력 채널 수
     * @return 리샘플링된 PCM 데이터 (16-bit little-endian)
     */
    fun resampleToBytes(
        inputData: FloatArray,
        inputSampleRate: Int,
        inputChannels: Int,
        outputSampleRate: Int,
        outputChannels: Int
    ): ByteArray {
        val samples = resample(
            inputData,
            inputSampleRate,
            inputChannels,
            outputSampleRate,
            outputChannels
        )

        return floatSamplesToBytes(samples)
    }

    /**
     * Byte 배열을 Float 샘플로 변환 (-1.0 ~ 1.0 범위)
     */
    private fun bytesToFloatSamples(data: ByteArray, channels: Int): FloatArray {
        val sampleCount = data.size / 2 / channels
        val samples = FloatArray(sampleCount * channels)

        for (i in 0 until sampleCount * channels) {
            val byteIdx = i * 2
            val value = readSample(data, byteIdx)
            samples[i] = value / 32768.0f  // Normalize to -1.0 ~ 1.0
        }

        return samples
    }

    /**
     * Float 샘플을 Byte 배열로 변환
     */
    private fun floatSamplesToBytes(samples: FloatArray): ByteArray {
        val data = ByteArray(samples.size * 2)

        for (i in samples.indices) {
            val value = (samples[i] * 32767.0f).toInt().coerceIn(-32768, 32767)
            writeSample(data, i * 2, value)
        }

        return data
    }

    /**
     * 채널 수를 변환합니다.
     * Stereo -> Mono: 두 채널의 평균
     * Mono -> Stereo: 동일한 데이터를 복제
     */
    private fun convertChannels(
        samples: FloatArray,
        inputChannels: Int,
        outputChannels: Int
    ): FloatArray {
        if (inputChannels == outputChannels) {
            return samples
        }

        val sampleCount = samples.size / inputChannels

        return when {
            // Stereo to Mono
            inputChannels == 2 && outputChannels == 1 -> {
                FloatArray(sampleCount) { i ->
                    val left = samples[i * 2]
                    val right = samples[i * 2 + 1]
                    (left + right) / 2.0f
                }
            }

            // Mono to Stereo
            inputChannels == 1 && outputChannels == 2 -> {
                FloatArray(sampleCount * 2) { i ->
                    samples[i / 2]
                }
            }

            else -> throw IllegalArgumentException(
                "Unsupported channel conversion: $inputChannels -> $outputChannels"
            )
        }
    }

    /**
     * 샘플 레이트를 변환합니다.
     * 선형 보간법(Linear Interpolation)을 사용합니다.
     */
    private fun resampleRate(
        samples: FloatArray,
        inputSampleRate: Int,
        outputSampleRate: Int
    ): FloatArray {
        if (inputSampleRate == outputSampleRate) {
            return samples
        }

        val ratio = outputSampleRate.toDouble() / inputSampleRate
        val outputSize = (samples.size * ratio).toInt()
        val output = FloatArray(outputSize)

        val invRatio = inputSampleRate.toDouble() / outputSampleRate

        for (i in output.indices) {
            val srcPos = i * invRatio
            val srcIdx = srcPos.toInt()
            val fraction = srcPos - srcIdx

            output[i] = interpolateSample(samples, srcIdx, fraction)
        }

        return output
    }

    /**
     * 선형 보간으로 샘플 값을 계산합니다.
     */
    private fun interpolateSample(
        samples: FloatArray,
        index: Int,
        fraction: Double
    ): Float {
        val idx1 = min(index, samples.size - 1)
        val idx2 = min(index + 1, samples.size - 1)

        val sample1 = samples[idx1]
        val sample2 = samples[idx2]

        val interpolated = sample1 + ((sample2 - sample1) * fraction).toFloat()

        // Clamp to valid range
        return interpolated.coerceIn(-1.0f, 1.0f)
    }

    /**
     * Little-endian 16-bit signed sample 읽기
     */
    private fun readSample(data: ByteArray, index: Int): Int {
        val low = data[index].toInt() and 0xFF
        val high = data[index + 1].toInt() and 0xFF
        val value = low or (high shl 8)

        return if (value > 32767) value - 65536 else value
    }

    /**
     * Little-endian 16-bit signed sample 쓰기
     */
    private fun writeSample(data: ByteArray, index: Int, value: Int) {
        val clamped = value.coerceIn(-32768, 32767)
        val unsigned = if (clamped < 0) clamped + 65536 else clamped

        data[index] = (unsigned and 0xFF).toByte()
        data[index + 1] = ((unsigned shr 8) and 0xFF).toByte()
    }
}

/**
 * TarsosDSP를 사용한 오디오 변환 유틸리티 클래스
 */
class AudioConverter {

    /**
     * 멀티채널 오디오를 모노로 변환
     *
     * @param inputSamples 입력 float 배열 (interleaved 형식)
     * @param inputChannels 입력 채널 수
     * @param useMean true면 채널 평균값 사용, false면 첫 번째 채널만 사용
     * @return 모노 float 배열
     */
    fun convertToMono(
        inputSamples: FloatArray,
        inputChannels: Int,
        useMean: Boolean = true
    ): FloatArray {
        if (inputChannels == 1) {
            return inputSamples.copyOf()
        }

        val monoSamples = FloatArray(inputSamples.size / inputChannels)

        if (useMean) {
            for (i in inputSamples.indices step inputChannels) {
                var sum = 0f
                for (j in 0 until inputChannels) {
                    sum += inputSamples[i + j]
                }
                monoSamples[i / inputChannels] = sum / inputChannels
            }
        } else {
            // 첫 번째 채널만 추출
            for (i in inputSamples.indices step inputChannels) {
                monoSamples[i / inputChannels] = inputSamples[i]
            }
        }

        return monoSamples
    }

    /**
     * Sample Rate 변환 (Resampling)
     *
     * @param inputSamples 입력 float 배열 (-1.0 ~ 1.0 범위)
     * @param sourceSampleRate 원본 샘플레이트 (예: 44100)
     * @param targetSampleRate 목표 샘플레이트 (예: 16000)
     * @param highQuality true면 고품질 리샘플링 (느림), false면 저품질 (빠름)
     * @return 변환된 float 배열
     */
    fun resample(
        inputSamples: FloatArray,
        sourceSampleRate: Int,
        targetSampleRate: Int,
        highQuality: Boolean = true
    ): FloatArray {
        if (sourceSampleRate == targetSampleRate) {
            return inputSamples.copyOf()
        }

        val factor = targetSampleRate.toDouble() / sourceSampleRate.toDouble()

        // Resampler 생성: minFactor, maxFactor는 변환 범위 지정
        val resampler = Resampler(highQuality, 0.1, 10.0)

        // 출력 버퍼 크기 계산 (여유 공간 추가)
        val outputSize = (inputSamples.size * factor + resampler.filterWidth * 2).toInt()
        val outputSamples = FloatArray(outputSize)

        // 리샘플링 수행
        val result = resampler.process(
            factor,
            inputSamples,
            0,
            inputSamples.size,
            true,  // lastBatch: 마지막 배치 여부
            outputSamples,
            0,
            outputSamples.size
        )

        // 실제 생성된 샘플만 반환
        return outputSamples.copyOf(result.outputSamplesGenerated)
    }

    /**
     * 채널 및 샘플레이트 모두 변환
     *
     * @param inputSamples 입력 float 배열 (interleaved 형식)
     * @param inputChannels 입력 채널 수
     * @param outputChannels 출력 채널 수 (현재 1만 지원)
     * @param sourceSampleRate 원본 샘플레이트
     * @param targetSampleRate 목표 샘플레이트
     * @return 변환된 모노 float 배열
     */
    fun convert(
        inputSamples: FloatArray,
        inputChannels: Int,
        outputChannels: Int,
        sourceSampleRate: Int,
        targetSampleRate: Int
    ): FloatArray {
        require(outputChannels == 1) { "현재 모노 출력만 지원합니다" }

        // Step 1: 멀티채널 → 모노 변환
        val monoSamples = convertToMono(inputSamples, inputChannels, useMean = true)

        // Step 2: 샘플레이트 변환
        return resample(monoSamples, sourceSampleRate, targetSampleRate)
    }

    /**
     * Byte 배열을 Float 배열로 변환 (16-bit PCM 가정)
     */
    fun bytesToFloats(bytes: ByteArray, isBigEndian: Boolean = false): FloatArray {
        val shorts = ShortArray(bytes.size / 2)
        val floats = FloatArray(shorts.size)

        for (i in shorts.indices) {
            val low: Int
            val high: Int
            if (isBigEndian) {
                high = bytes[i * 2].toInt() and 0xFF
                low = bytes[i * 2 + 1].toInt() and 0xFF
            } else {
                low = bytes[i * 2].toInt() and 0xFF
                high = bytes[i * 2 + 1].toInt() and 0xFF
            }
            shorts[i] = ((high shl 8) or low).toShort()
            floats[i] = shorts[i].toFloat() / 32768f
        }

        return floats
    }

    /**
     * Float 배열을 Byte 배열로 변환 (16-bit PCM)
     */
    fun floatsToBytes(floats: FloatArray, isBigEndian: Boolean = false): ByteArray {
        val bytes = ByteArray(floats.size * 2)

        for (i in floats.indices) {
            // Clamp to -1.0 ~ 1.0
            val clamped = floats[i].coerceIn(-1f, 1f)
            val shortVal = (clamped * 32767f).toInt().toShort()

            if (isBigEndian) {
                bytes[i * 2] = (shortVal.toInt() shr 8).toByte()
                bytes[i * 2 + 1] = shortVal.toByte()
            } else {
                bytes[i * 2] = shortVal.toByte()
                bytes[i * 2 + 1] = (shortVal.toInt() shr 8).toByte()
            }
        }

        return bytes
    }
}

object AudioConfig {
    const val SAMPLE_RATE = 16000
    const val CHANNELS = 1
    const val WHISPER_CHUNK_SECONDS = 30

    /**
     * SamplingRate * 30초 * 채널 수
     */
    const val WHISPER_CHUNK_SAMPLES = SAMPLE_RATE * WHISPER_CHUNK_SECONDS * CHANNELS

    /**
     * WHISPER_CHUNK_SAMPLES(SamplingRate * 30초 * 채널 수)
     */
    const val PROCESSING_CHUNK_SAMPLES = WHISPER_CHUNK_SAMPLES
}