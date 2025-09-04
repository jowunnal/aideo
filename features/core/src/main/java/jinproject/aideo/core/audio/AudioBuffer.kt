package jinproject.aideo.core.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
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
    private val localFileDataSource: LocalFileDataSource,
) {
    private val audioBuffer = Channel<FloatArray>(capacity = Channel.BUFFERED)

    private lateinit var samplingAudioList: ByteArray
    var lastProducedAudioIndex = 0

    private val extractor by lazy {
        MediaExtractor() // 미디어 데이터를 인코딩, demux(여러 트랙으로 분리)하여 추출하는 미디어 추출기 인스턴스
    }

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

        /**
         * 비디오로 부터 음성을 추출하여, whisper 모델의 입력에 맞게 전처리한 오디오 데이터를 생산하는 생산자 코루틴
         *
         * 전체 플로우
         *1. MediaCodec 으로 비디오 파일에서 음성 트랙을 추출 및 디코딩
         * 2. 디코드 된 오디오(Byte)를 16kz sample rate, 모노 채널로 reSampling 후, float32 타입으로 정규화
         * 3. whisper 모델의 입력에 맞게 전처리된 floatArray 를 STT 추론을 담당하는 소비자 코루틴에게 전송
         */
        val extractorJob = launch {
            extractAudioData(videoFileUri)
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
    @OptIn(
        DelicateCoroutinesApi::class,
        ExperimentalAtomicApi::class,
        ExperimentalCoroutinesApi::class
    )
    private suspend fun startConsumer(
        producerJob: Job,
        languageCode: String,
        transcribe: (FloatArray, Float, Int, String) -> String,
    ): String {
        var inferenceResult = InferenceResult(index = 0, lastSeconds = 0f)
        val transcription = StringBuilder()

        runCatching {
            while (producerJob.isActive || !audioBuffer.isEmpty) {
                val segment = audioBuffer.receive()

                Log.d("test", "세그먼트 수신: ${segment.size}")
                if (segment.isNotEmpty()) {
                    val srtResult = transcribe(
                        segment,
                        inferenceResult.lastSeconds,
                        inferenceResult.index,
                        languageCode
                    )
                    Log.d("test", "세그먼트 내의 srtResult: $srtResult")
                    inferenceResult =
                        parseLastEndTimeFromSrt(srtText = srtResult, origin = inferenceResult)
                    Log.d(
                        "test",
                        "lastTimestamp: ${inferenceResult.lastSeconds}, lastIndex: ${inferenceResult.index}"
                    )

                    transcription.append(srtResult)
                }
            }
        }.onFailure { t ->
            Log.e("test", "소비자 오류: ${t.message}")
        }.onSuccess {
            Log.d("test", "소비자 종료")
        }

        return transcription.toString()
    }

    /**
     * SRT 텍스트에서 마지막 종료 시간 파싱
     */
    private fun parseLastEndTimeFromSrt(srtText: String, origin: InferenceResult): InferenceResult {
        val pattern = """-->\s*(\d{2}):(\d{2}):(\d{2}),(\d{3})""".toRegex()
        val matches = pattern.findAll(srtText)

        if (matches.none())
            return InferenceResult(
                index = origin.index,
                lastSeconds = origin.lastSeconds + 30
            )

        val lastMatch = matches.last()
        val hours = lastMatch.groupValues[1].toInt()
        val minutes = lastMatch.groupValues[2].toInt()
        val seconds = lastMatch.groupValues[3].toInt()
        val milliseconds = lastMatch.groupValues[4].toInt()

        val lastEndPos = lastMatch.range.first

        val textBeforeLastEnd = srtText.substring(0, lastEndPos)
        val lines = textBeforeLastEnd.lines().asReversed()

        return InferenceResult(
            index = lines.firstOrNull { it.trim().matches(Regex("^\\d+$")) }?.trim()?.toInt() ?: 0,
            lastSeconds = hours * 3600 + minutes * 60 + seconds + milliseconds / 1000.0f,
        )
    }

    private data class InferenceResult(
        val index: Int,
        val lastSeconds: Float,
    )

    /**
     * 미디어 데이터에서 오디오 트랙을 추출하여 디코딩 후 전처리 함수를 호출하는 함수
     *
     * 1. MediaExtractor 로 미디어 파일에서 오디오 트랙을 추출
     * 2. MediaCodec 으로 추출된 오디오 트랙을 디코딩
     * 3. 디코드된 오디오 데이터(ByteArray)로 전처리 함수 호출
     *
     * @param videoContentUri 비디오 contentUri
     */
    @OptIn(ExperimentalAtomicApi::class)
    suspend fun extractAudioData(videoContentUri: Uri) {
        extractor.setDataSource(context, videoContentUri, null)

        val audioTrack = "audio/"
        var audioTrackIndex = -1
        var format: MediaFormat? = null
        var mime: String? = null

        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val trackMime = trackFormat.getString(MediaFormat.KEY_MIME)
            if (trackMime != null && trackMime.startsWith(audioTrack)) {
                audioTrackIndex = i
                format = trackFormat
                mime = trackMime
                break
            }
        }

        if (audioTrackIndex == -1 || format == null) { // 추출된 비디오에 "audio/" 트랙이 없었을 경우
            extractor.release()
            throw IOException("No audio track found")
        }

        extractor.selectTrack(audioTrackIndex) // 추출기의 track 을 "audio/" 로 설정
        val decoder = MediaCodec.createDecoderByType(mime!!)

        decoder.configure(format, null, null, 0)
        decoder.start()

        val info = MediaCodec.BufferInfo()
        val timeoutUs = 5000L

        coroutineScope {
            launch {
                var isEOS = false

                while (!isEOS) {
                    val inputBufferId = decoder.dequeueInputBuffer(timeoutUs)
                    if (inputBufferId >= 0) { // decoder 에서 유효한 값을 획득
                        val inputBuffer = decoder.getInputBuffer(inputBufferId)
                        val sampleSize = extractor.readSampleData(inputBuffer!!, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputBufferId, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isEOS = true
                        } else {
                            decoder.queueInputBuffer(
                                inputBufferId, 0, sampleSize, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }
            }

            launch {
                var isEOS = false
                while (!isEOS) {
                    val outputBufferId = decoder.dequeueOutputBuffer(info, timeoutUs)

                    if (outputBufferId >= 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputBufferId)

                        if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        else if (info.size != 0) {
                            outputBuffer?.let {
                                produceAudio(
                                    audioData = it,
                                    format = format,
                                    bufferSize = info.size
                                )
                            }
                        }

                        decoder.releaseOutputBuffer(outputBufferId, false)
                    }
                }
            }
        }

        if (samplingAudioList.find { it != 0x00.toByte() } != null) {
            audioBuffer.send(
                normalizeAudioSample(
                    audioChunk = samplingAudioList,
                    format = format
                )
            )
            Log.d("test", "남겨진 데이터 전송 완료")
        }

        Log.d("test", "전송된 포맷 mime: ${format.getString(MediaFormat.KEY_MIME)}")

        decoder.release()
        extractor.release()
        Log.d("test", "생산자 종료")
    }

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
        format: MediaFormat,
        bufferSize: Int,
    ) {
        runCatching {
            val originSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val stepSize = originSampleRate * 30 * Float.SIZE_BYTES

            if (::samplingAudioList.isInitialized.not())
                samplingAudioList = ByteArray(stepSize)

            if (lastProducedAudioIndex + bufferSize > stepSize) {
                Log.d("test", "전송전 데이터양: ${samplingAudioList.size}")
                val resampled = normalizeAudioSample(
                    audioChunk = samplingAudioList,
                    format = format
                )

                audioBuffer.send(
                    resampled
                )

                saveFloatArrayAsWav(
                    floatArray = resampled
                )

                Log.d(
                    "test",
                    "전송된 데이터 양: ${resampled.size} , 증감: ${resampled.size - samplingAudioList.size}"
                )

                repeat(samplingAudioList.size) { idx ->
                    samplingAudioList[idx] = 0x00.toByte()
                }
                lastProducedAudioIndex = 0
            }

            repeat(bufferSize) { idx ->
                samplingAudioList[lastProducedAudioIndex++] = audioData[idx]
            }

        }.onFailure { t ->
            Log.d("test", "error occurred ${t.message}")
        }
    }

    /**
     * 오디오 데이터(ByteArray) 를 전처리 하는 함수
     *
     * 목적) Sample Rate - 16000, Channel - 1(모노) 형식으로 오디오 데이터를 변환
     */
    private fun normalizeAudioSample(
        audioChunk: ByteArray,
        format: MediaFormat,
    ): FloatArray {
        val originalSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val originalChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val shortBuffer =
            ByteBuffer.wrap(audioChunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
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
        extractor.release()
    }

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