package jinproject.aideo.core.audio

import android.R.attr.stepSize
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.Byte
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.roundToInt

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
 * 오디오 추출, 변환, 추론, 후처리의 작업들을 실행하는 클래스
 */
class AudioBuffer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localPlayerDataSource: LocalPlayerDataSource,
) {
    private val audioBuffer = Channel<FloatArray>(Channel.UNLIMITED)
    private val transcription = StringBuilder()

    private var lastTimestamp = 0.0f
    private var lastInferenceIndex = 0

    private var totalAudioDataSize = 4

    private var processedAudioData by mutableFloatStateOf(0f)

    val processedAudioProgress by derivedStateOf {
        (processedAudioData / (totalAudioDataSize / 12)).coerceAtMost(1f)
    }

    private val samplingAudioList = ByteArray(WHISPER_CHUNK_SAMPLES * 12)
    var lastProducedAudioIndex = 0

    @OptIn(ExperimentalAtomicApi::class)
    val isProducerFinished = AtomicBoolean(false)

    /**
     * 전체 오디오 추출 - 정규화 - 추론(텍스트 변환) 과정 수행을 시작하는 함수
     *
     * @param videoFileUri 비디오 파일의 contentUri
     * @param transcribe 추론을 수행할 람다
     */
    suspend fun processFullAudio(
        videoFileUri: Uri,
        transcribe: (FloatArray, Float, Int, String) -> String,
    ): String = withContext(Dispatchers.Default) {
        val languageCode = "ko"

        val extractorJob = launch {
            extractAudioData(videoFileUri)
        }

        val consumerJob = launch(Dispatchers.IO) {
            startConsumer(
                languageCode = languageCode,
                transcribe = transcribe,
            )
        }

        joinAll(extractorJob, consumerJob)

        transcription.toString().also {
            transcription.clear()
        }
    }


    /*
    private var previousOverlap = FloatArray(OVERLAP_SAMPLES)
    /**
     * 오버랩 적용
     */
    private fun applyOverlap(samples: FloatArray): FloatArray {
        if (previousOverlap.isEmpty()) {
            return samples
        }

        val result = FloatArray(samples.size + previousOverlap.size)
        System.arraycopy(previousOverlap, 0, result, 0, previousOverlap.size)
        System.arraycopy(samples, 0, result, previousOverlap.size, samples.size)

        return result
    }

    /**
     * 다음 세그먼트를 위한 오버랩 저장
     */
    private fun saveOverlapForNext(samples: FloatArray) {
        if (samples.size >= OVERLAP_SAMPLES) {
            val startIndex = samples.size - OVERLAP_SAMPLES
            previousOverlap = samples.sliceArray(startIndex until samples.size)
        }
    }*/

    /**
     * 샘플링 및 정규화된 오디오 데이터를 받아, 추론 엔진의 로직을 호출하는 소비자 함수
     *
     * 1. audioBuffer 로 전송된 샘플링 및 정규화된 오디오 floatArray 를 수신
     * 2. whisper 모델의 cpp 추론 엔진의 JNI 함수를 호출(추론 수행)
     * 3. 추론 결과(srt 포맷의 텍스트)를 후처리 한 후, 저장
     *
     * 30초 분량의 오디오로 분할하여 추론하기 때문에 srt 포맷의 형식을 위한 후처리가 필요
     */
    @OptIn(DelicateCoroutinesApi::class, ExperimentalAtomicApi::class)
    private suspend fun startConsumer(
        languageCode: String,
        transcribe: (FloatArray, Float, Int, String) -> String,
    ) {
        while (processedAudioProgress < 1f && !isProducerFinished.load()) {
            val segment = audioBuffer.receive()

            Log.d("test", "세그먼트 수신: ${segment.size}")
            val srtResult = transcribe(segment, lastTimestamp, lastInferenceIndex, languageCode)
            Log.d("test", "세그먼트 내의 srtResult: $srtResult")
            parseLastEndTimeFromSrt(srtResult)
            Log.d("test", "lastTimestamp: $lastTimestamp, lastIndex: $lastInferenceIndex")

            processedAudioData += WHISPER_CHUNK_SAMPLES
            transcription.append(srtResult)
        }
        try {
        } catch (e: Exception) {
            Log.e("test", "소비자 오류: ${e.message}")
        } finally {
            Log.d("test", "소비자 종료: $totalAudioDataSize")
        }
    }

    /**
     * SRT 텍스트에서 마지막 종료 시간 파싱
     */
    private fun parseLastEndTimeFromSrt(srtText: String) {
        val pattern = """-->\s*(\d{2}):(\d{2}):(\d{2}),(\d{3})""".toRegex()
        val matches = pattern.findAll(srtText)

        if (matches.none())
            return

        val lastMatch = matches.last()
        val hours = lastMatch.groupValues[1].toInt()
        val minutes = lastMatch.groupValues[2].toInt()
        val seconds = lastMatch.groupValues[3].toInt()
        val milliseconds = lastMatch.groupValues[4].toInt()

        val lastEndPos = lastMatch.range.first

        val textBeforeLastEnd = srtText.substring(0, lastEndPos)
        val lines = textBeforeLastEnd.lines().asReversed()

        lastInferenceIndex =
            lines.firstOrNull { it.trim().matches(Regex("^\\d+$")) }?.trim()?.toInt() ?: 0
        lastTimestamp = hours * 3600 + minutes * 60 + seconds + milliseconds / 1000.0f
    }

    /**
     * 비디오로 부터 음성을 추출하여, whisper 모델의 입력에 맞게 변환하는 함수
     *
     * 1. MediaCodec 으로 비디오 파일에서 음성을 추출 및 디코딩
     * 2. 디코드 된 오디오(Byte)를 16kz, 모노로 reSampling 후, float32 타입으로 정규화
     * 3. whisper 모델의 입력에 맞게 변환된 floatArray 를 audioBuffer 채널에 send
     *
     * @param videoContentUri 비디오 contentUri
     */
    @OptIn(ExperimentalAtomicApi::class)
    suspend fun extractAudioData(videoContentUri: Uri) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, videoContentUri, null)

        var audioTrackIndex = -1
        var format: MediaFormat? = null
        var mime: String? = null

        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val trackMime = trackFormat.getString(MediaFormat.KEY_MIME)
            if (trackMime != null && trackMime.startsWith("audio/")) {
                audioTrackIndex = i
                format = trackFormat
                mime = trackMime
                break
            }
        }

        if (audioTrackIndex == -1 || format == null || mime == null) {
            extractor.release()
            throw IOException("No audio track found")
        }

        extractor.selectTrack(audioTrackIndex)
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val info = MediaCodec.BufferInfo()
        var isEOS = false
        val timeoutUs = 10000L

        totalAudioDataSize = 0

        while (!isEOS) {
            val inputBufferId = decoder.dequeueInputBuffer(timeoutUs)
            if (inputBufferId >= 0) {
                val inputBuffer = decoder.getInputBuffer(inputBufferId)
                val sampleSize = extractor.readSampleData(inputBuffer!!, 0)
                if (sampleSize < 0) {
                    decoder.queueInputBuffer(
                        inputBufferId, 0, 0, 0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    isEOS = true
                } else {
                    val presentationTimeUs = extractor.sampleTime
                    decoder.queueInputBuffer(
                        inputBufferId, 0, sampleSize, presentationTimeUs, 0
                    )
                    extractor.advance()
                }
            }

            var outputBufferId = decoder.dequeueOutputBuffer(info, timeoutUs)

            while (outputBufferId >= 0) {
                val outputBuffer = decoder.getOutputBuffer(outputBufferId)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0 || info.size != 0) {
                    val chunk = ByteArray(info.size)
                    outputBuffer?.get(chunk)
                    outputBuffer?.clear()
                    
                    produceAudio(audioData = chunk, format = format)
                    totalAudioDataSize += chunk.size

                    decoder.releaseOutputBuffer(outputBufferId, false)
                    outputBufferId = decoder.dequeueOutputBuffer(info, timeoutUs)
                }
            }
        }

        if (samplingAudioList.find { it != 0x00.toByte() } != null)
            audioBuffer.send(normalizeAudioSample(audioChunk = samplingAudioList, format = format))

        decoder.stop()
        decoder.release()
        extractor.release()
        isProducerFinished.store(true)
        Log.d("test", "생산자 종료")
    }

    /**
     * whisper 모델의 입력 구조에 맞게 변환된 오디오 데이터를 30초 분량의 크기로 모아, 채널로 전송하는 함수
     *
     * 1. 오디오를 30초 * 16k(sampling rate) * 1(모노채널) 분량이 될 때 까지 list에 축적
     * 2. 30초 분량 이상이 모이면, 소비자 코루틴이 처리할 수 있도록 채널에 전송
     * 3. 전송 후, 메모리 효율화를 위해 전송한 만큼의 오디오 데이터는 제거
     * --> List 사용(데이터 삭제 작업에 효율적, 탐색은 소비자가 처리하기 때문에 고려사항이 아님)
     * --> 유의한 점: deep copy 를 위한 slice 이용
     *
     * @param audioData 오디오 데이터(floatArray)
     */
    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun produceAudio(
        audioData: ByteArray,
        format: MediaFormat,
    ) {
        runCatching {
            val stepSize = WHISPER_CHUNK_SAMPLES * 12

            if (lastProducedAudioIndex + audioData.size > stepSize) {
                audioBuffer.send(
                    normalizeAudioSample(
                        audioChunk = samplingAudioList,
                        format = format
                    )
                )
                Log.d("test","오디오 전송: ${samplingAudioList.size}")

                repeat(samplingAudioList.size) { idx ->
                    samplingAudioList[idx] = 0x00.toByte()
                }
                lastProducedAudioIndex = 0
            }

            repeat(audioData.size) { idx ->
                samplingAudioList[lastProducedAudioIndex++] = audioData[idx]
            }

        }.onFailure { t ->
            Log.d("test", "error occurred ${t.message}")
        }
    }

    /**
     * 오디오 데이터를 float32 타입으로 정규화 하는 함수
     */
    private fun normalizeAudioSample(
        audioChunk: ByteArray,
        format: MediaFormat,
    ): FloatArray {
        val originalSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val originalChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val shortBuffer =
            ByteBuffer
                .wrap(audioChunk)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()

        val shortArray = ShortArray(shortBuffer.limit())
        shortBuffer.get(shortArray)

        val monoShortArray = if (originalChannels == 2) {
            ShortArray(shortArray.size / 2) { i ->
                (((shortArray[2 * i].toInt() + shortArray[2 * i + 1].toInt()) / 2).toShort())
            }
        } else {
            shortArray
        }

        val reSampledShortArray = if (originalSampleRate != SAMPLE_RATE) {
            linearResample(monoShortArray, originalSampleRate)
        } else {
            monoShortArray
        }

        return convertToFloat32AndNormalize(reSampledShortArray)
    }

    private fun linearResample(input: ShortArray, srcRate: Int): ShortArray {
        val ratio = SAMPLE_RATE.toDouble() / srcRate
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

    private fun convertToFloat32AndNormalize(shortArray: ShortArray): FloatArray {
        return FloatArray(shortArray.size) { i ->
            shortArray[i].toFloat() / 32768.0f
        }
    }

    fun release() {
        audioBuffer.close()
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNELS = 1
        const val WHISPER_CHUNK_SECONDS = 30
        const val OVERLAP_SECONDS = 10

        const val WHISPER_CHUNK_SAMPLES = SAMPLE_RATE * WHISPER_CHUNK_SECONDS * CHANNELS
        const val OVERLAP_SAMPLES = SAMPLE_RATE * OVERLAP_SECONDS * CHANNELS
        const val PROCESSING_CHUNK_SAMPLES = WHISPER_CHUNK_SAMPLES
    }
}