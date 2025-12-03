package jinproject.aideo.core.audio

import android.util.Log
import androidx.core.net.toUri
import jinproject.aideo.core.lite.SpeechToText.Companion.TS_STEP
import jinproject.aideo.core.lite.VocabUtils
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import javax.inject.Inject
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.roundToInt
import kotlin.text.Charsets.UTF_8

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
    private val localFileDataSource: LocalFileDataSource,
    private val mediaFileManager: MediaFileManager,
    private val vocabUtils: VocabUtils,
) {
    private val audioBuffer = Channel<FloatArray>(capacity = Channel.BUFFERED)

    private lateinit var samplingAudioList: ByteArray
    var lastProducedAudioIndex = 0

    /**
     * 전체 오디오 추출 - 정규화 - 추론(텍스트 변환) 과정 수행을 시작하는 함수
     *
     * @param videoFileUri 비디오 파일의 contentUri
     * @param transcribe 추론을 수행할 람다
     */
    suspend fun processFullAudio(
        videoFileUri: String,
        transcribe: suspend (FloatArray, String) -> FloatArray,
    ): String = withContext(Dispatchers.Default) {
        val languageCode = "ko"

        /**
         * 비디오로 부터 음성을 추출하여, whisper 모델의 입력에 맞게 전처리한 오디오 데이터를 생산하는 생산자 코루틴
         *
         * 전체 플로우
         * 1. MediaCodec 으로 비디오 파일에서 음성 트랙을 추출 및 디코딩
         * 2. 디코드 된 오디오(Byte)를 16kz sample rate, 모노 채널로 reSampling 후, float32 타입으로 정규화
         * 3. whisper 모델의 입력에 맞게 전처리된 floatArray 를 STT 추론을 담당하는 소비자 코루틴에게 전송
         */
        val extractorJob = launch {
            val mediaInfo = mediaFileManager.extractAudioData(videoFileUri.toUri(), ::produceAudio)

            if(lastProducedAudioIndex != 0) {
                clearAudioBuffer(mediaInfo)
            }
        }

        /**
         * 샘플링 및 정규화된 오디오 데이터를 받아, 추론 엔진의 로직을 호출하는 소비자
         *
         * 1. audioBuffer 로 전송된 샘플링 및 정규화된 오디오 floatArray 를 수신
         * 2. whisper 모델의 cpp 추론 엔진의 JNI 함수를 호출(추론 수행)
         * 3. 추론 결과(srt 포맷의 텍스트)를 후처리 한 후, 저장
         *
         * 30초 분량의 오디오로 분할하여 추론하기 때문에 srt 포맷의 형식을 위한 후처리가 필요
         */
        val consumerJob = async(Dispatchers.IO) {
            startConsumer(
                producerJob = extractorJob,
                languageCode = languageCode,
                transcribe = transcribe,
            )
        }

        joinAll(extractorJob, consumerJob)

        consumerJob.await()
    }

    /**
     * 분할된 오디오 데이터를 추론하여 결과를 자막 형식에 맞게 모아놓는 함수
     *
     * 1. 30초 단위로 분할되어 채널에 들어오는 전처리된 오디오 데이터를 추론 수행
     * 2. 추론 결과로 반환된 30초 분량의 자막 텍스트를 [transcription] 에 합산
     *
     * @param languageCode: 언어 코드
     * @param transcribe: STT 추론 함수
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun startConsumer(
        producerJob: Job,
        languageCode: String,
        transcribe: suspend (FloatArray, String) -> FloatArray,
    ): String {
        val inferenceInfo = InferenceInfo(index = 0, lastSeconds = 0f)
        val transcription = StringBuilder()

        while (producerJob.isActive || !audioBuffer.isEmpty) {
            val segment = audioBuffer.receive()
            saveFloatArrayAsWav(
                floatArray = segment
            )

            Log.d("test", "세그먼트 수신: ${segment.size}")
            if (segment.isNotEmpty()) {
                val inferenceResult = transcribe(
                    segment,
                    languageCode
                )

                val srtResult = convertToSRT(
                    inferenceResult,
                    inferenceInfo,
                )

                Log.d("test", "세그먼트 내의 srtResult: $srtResult")

                parseLastEndTimeFromSrt(srtText = srtResult, inferenceInfo = inferenceInfo)

                Log.d(
                    "test",
                    "lastTimestamp: ${inferenceInfo.lastSeconds}, lastIndex: ${inferenceInfo.index}"
                )

                transcription.append(srtResult)
            }
        }


        return transcription.toString()
    }

    private fun convertToSRT(
        output: FloatArray,
        inferenceInfo: InferenceInfo,
    ): String {
        val segments = mutableListOf<Triple<Double, Double, ByteArray>>()
        val currTextTokens = mutableListOf<Byte>()
        var currStart = 0.0
        var hasStart = false

        for (i in 0 until output.size) {
            val token = output[i].toInt()

            when (token) {
                in 0 until VocabUtils.EOT -> currTextTokens.addAll(
                    vocabUtils.getWordByToken(token)!!.toList()
                )

                VocabUtils.EOT -> break
                in VocabUtils.BEGIN..VocabUtils.END -> {
                    val ts = TS_STEP * (token - VocabUtils.BEGIN) + inferenceInfo.lastSeconds
                    if (!hasStart) {
                        // 시작 타임스탬프
                        currStart = ts
                        hasStart = true
                    } else {
                        // 끝 타임스탬프
                        if (currTextTokens.isNotEmpty()) {
                            segments.add(Triple(currStart, ts, currTextTokens.toByteArray()))
                        }
                        currTextTokens.clear()
                        currStart = ts
                    }
                }
            }
        }

        // 마지막 텍스트 처리
        if (hasStart && currTextTokens.isNotEmpty()) {
            val endTs = currStart + 30
            segments.add(Triple(currStart, endTs, currTextTokens.toByteArray()))
        }

        // SRT 변환
        val srt = StringBuilder()
        for ((idx, segment) in segments.withIndex()) {
            val (start, end, text) = segment
            srt.append("${idx + 1 + inferenceInfo.index}\n")
            srt.append("${formatTimestamp(start)} --> ${formatTimestamp(end)}\n")
            srt.append("${String(text, UTF_8).trim()}\n\n")
        }

        return srt.toString()
    }

    private fun formatTimestamp(t: Double): String {
        val h = (t / 3600).toInt()
        val m = ((t % 3600) / 60).toInt()
        val s = (t % 60).toInt()
        val ms = ((t - t.toInt()) * 1000).toInt()
        return String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d,%03d",
            h,
            m,
            s,
            ms
        )
    }

    /**
     * SRT 텍스트에서 마지막 종료 시간 파싱
     */
    private fun parseLastEndTimeFromSrt(srtText: String, inferenceInfo: InferenceInfo) {
        val pattern = """-->\s*(\d{2}):(\d{2}):(\d{2}),(\d{3})""".toRegex()
        val matches = pattern.findAll(srtText)

        if (matches.none()) {
            inferenceInfo.apply {
                index = inferenceInfo.index
                lastSeconds = inferenceInfo.lastSeconds + 30
            }
            return
        }

        val lastMatch = matches.last()
        val hours = lastMatch.groupValues[1].toInt()
        val minutes = lastMatch.groupValues[2].toInt()
        val seconds = lastMatch.groupValues[3].toInt()
        val milliseconds = lastMatch.groupValues[4].toInt()

        val lastEndPos = lastMatch.range.first

        val textBeforeLastEnd = srtText.substring(0, lastEndPos)
        val lines = textBeforeLastEnd.lines().asReversed()

        inferenceInfo.apply {
            index = lines.firstOrNull { it.trim().matches(Regex("^\\d+$")) }?.trim()?.toInt() ?: 0
            lastSeconds = hours * 3600 + minutes * 60 + seconds + milliseconds / 1000.0f
        }
    }

    private data class InferenceInfo(
        var index: Int,
        var lastSeconds: Float,
    )

    /**
     * 오디오 데이터를 전처리하여 소비자 코루틴에게 전송하는 함수
     *
     * 1. 오디오를 30초 * 16kHz(sampling rate) * 1(모노채널) 분량이 될 때 까지 배열에 축적
     * 2. 30초 분량 이상이 모이면, 소비자 코루틴이 이후 과정을 수행할 수 있도록 채널에 전송
     * 3. 전송 후, 할당된 메모리를 재 사용하기 위해 기존 배열을 기본값(0)으로 초기화
     *
     * @param audioData 오디오 데이터
     */
    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun produceAudio(
        audioData: ByteBuffer,
        audioSize: Int,
        mediaInfo: MediaInfo,
    ) {
        runCatching {
            val stepSize = mediaInfo.sampleRate * 30 * Float.SIZE_BYTES

            if (::samplingAudioList.isInitialized.not())
                samplingAudioList = ByteArray(stepSize)

            if (lastProducedAudioIndex + audioSize > stepSize) {
                Log.d("test", "전송전 데이터양: ${samplingAudioList.size}")

                clearAudioBuffer(mediaInfo)
            }

            repeat(audioSize) { idx ->
                samplingAudioList[lastProducedAudioIndex++] = audioData[idx]
            }

        }.onFailure { t ->
            Log.d("test", "error occurred ${t.message}")
        }
    }

    suspend fun clearAudioBuffer(
        mediaFormat: MediaInfo,
    ) {
        val normalizedAudio = normalizeAudioSample(
            audioChunk = samplingAudioList,
            sampleRate = mediaFormat.sampleRate,
            channelCount = mediaFormat.channelCount
        )

        audioBuffer.send(normalizedAudio)

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
     * 오디오 데이터(ByteArray) 를 전처리 하는 함수
     *
     * 목적) Sample Rate - 16000, Channel - 1(모노) 형식으로 오디오 데이터를 변환
     */
    private fun normalizeAudioSample(
        audioChunk: ByteArray,
        sampleRate: Int,
        channelCount: Int,
    ): FloatArray {

        val shortBuffer =
            ByteBuffer.wrap(audioChunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val shortArray = ShortArray(shortBuffer.limit())
        shortBuffer.get(shortArray)

        val monoShortArray = if (channelCount == 2) {
            ShortArray(shortArray.size / 2) { i ->
                (((shortArray[2 * i].toInt() + shortArray[2 * i + 1].toInt()) / 2).toShort())
            }
        } else {
            shortArray
        }

        val reSampledShortArray = if (sampleRate != SAMPLE_RATE) {
            linearResample(monoShortArray, sampleRate)
        } else {
            monoShortArray
        }

        return convertToFloatArray(reSampledShortArray)
    }

    /**
     * 선형 보간법을 이용하여 Sample Rate 를 16000 으로 변환하는 함수
     */
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

    /**
     * ShortArray 를 FloatArray 로 변환하는 함수
     */
    private fun convertToFloatArray(shortArray: ShortArray): FloatArray {
        return FloatArray(shortArray.size) { i ->
            shortArray[i].toFloat() / Short.MAX_VALUE.toFloat()
        }
    }

    /**
     * Test 용도로 음성 데이터(FloatArray)를 오디오 파일로 저장하는 함수
     */
    private fun saveFloatArrayAsWav(
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

    fun release() {
        audioBuffer.close()
    }

    data class MediaInfo(
        val sampleRate: Int,
        val channelCount: Int,
    )

    companion object {
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
}