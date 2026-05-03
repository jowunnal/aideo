package jinproject.aideo.core.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.net.toUri
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jinproject.aideo.core.media.AudioSamplingBit.PCM16Bit
import jinproject.aideo.core.media.AudioSamplingBit.PCM32Bit
import jinproject.aideo.core.media.AudioSamplingBit.PCM8Bit
import jinproject.aideo.core.media.audio.AudioConfig
import jinproject.aideo.data.FileIdentifier
import jinproject.aideo.data.SubtitleFileConfig
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaFileManagerModule {

    @Binds
    abstract fun bindsVideoFileManager(
        androidMediaFileManager: AndroidMediaFileManager,
    ): MediaFileManager
}

interface MediaFileManager {
    suspend fun getVideoInfo(videoUriString: String): VideoItem?
    suspend fun <T> extractAudioData(
        videoContentUri: Uri,
        extractedAudioChannel: SendChannel<T>,
        audioPreProcessor: AudioPreProcessor<T>
    )
}

class AndroidMediaFileManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val localFileDataSource: LocalFileDataSource,
) : MediaFileManager {

    override suspend fun getVideoInfo(videoUriString: String): VideoItem? =
        withContext(Dispatchers.IO) {
            val videoUri = videoUriString.toUri()
            var name: String? = null
            var date: Long? = null

            val projection = arrayOf(
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_TAKEN,
            )

            try {
                context.contentResolver.query(
                    videoUri,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex =
                            cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)

                        val dateIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)

                        if (nameIndex != -1)
                            name = cursor.getString(nameIndex)

                        if (dateIndex != -1)
                            date = cursor.getLong(dateIndex)

                        Timber.tag("test").d("Queried Video Info[name: $name, date: $date]")
                    }
                }
            } catch (exception: SecurityException) {
                Timber.w(exception, "No permission to access video uri: %s", videoUri)
                return@withContext null
            }

            if (name != null && date != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        videoUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (exception: SecurityException) {
                    Timber.w(exception, "Failed to persist video uri permission: %s", videoUri)
                    return@withContext null
                }

                val thumbnailPath = SubtitleFileConfig.toSubtitleFileId(videoUri.toString())
                    ?.let {
                        val thumbnailIdentifier = SubtitleFileConfig.toThumbnailFileIdentifier(it)

                        createThumbnailAndGetAbsolutePath(
                            uri = videoUri,
                            thumbnailFileIdentifier = thumbnailIdentifier
                        )
                    }

                VideoItem(
                    uri = videoUri.toString(),
                    title = name!!,
                    thumbnailAbsolutePath = thumbnailPath,
                    date = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(date!!),
                        ZoneId.systemDefault()
                    ).format(
                        DateTimeFormatter.ofPattern("yyyy.MM.dd")
                    )
                )
            } else
                null
        }

    private fun createThumbnailAndGetAbsolutePath(
        uri: Uri,
        thumbnailFileIdentifier: FileIdentifier,
    ): String? {
        return MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)

            val timeUs = 5 * 1_000_000L
            val bitmap =
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

            localFileDataSource.createFileAndWriteOnOutputStream(
                fileIdentifier = thumbnailFileIdentifier,
                writeContentOnFile = { outputStream ->
                    bitmap?.compress(
                        Bitmap.CompressFormat.JPEG,
                        90,
                        outputStream
                    ) == true
                }
            )
        }
    }

    var audioInfo: AudioInfo? = null
        private set

    /**
     * 미디어 데이터에서 오디오 트랙을 비동기로 추출
     *
     * 1. MediaExtractor 로 미디어 파일에서 오디오 트랙을 추출
     * 2. MediaCodec 으로 추출된 오디오 트랙을 wav format 으로 decode
     * 3. 디코딩된 오디오 데이터를 전처리 하여 채널로 전송
     *
     * @param videoContentUri 비디오 contentUri
     */
    @OptIn(ExperimentalAtomicApi::class)
    override suspend fun <T> extractAudioData(
        videoContentUri: Uri,
        extractedAudioChannel: SendChannel<T>,
        audioPreProcessor: AudioPreProcessor<T>
    ) {
        val extractor = MediaExtractor() // 미디어 데이터를 인코딩, demux(여러 트랙으로 분리)하여 추출하는 미디어 추출기 인스턴스
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

        if (audioTrackIndex == -1 || format == null) { // 추출된 비디오에 오디오 트랙이 없었을 경우
            extractor.release()
            throw IOException("No audio track found")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2)
            format.setInteger(MediaFormat.KEY_MAX_OUTPUT_CHANNEL_COUNT, 1)
        else
            format.setInteger(MediaFormat.KEY_AAC_MAX_OUTPUT_CHANNEL_COUNT, 1)

        // AudioExtractor로 너무 적은 크기로 빈번하게 추출하여 디코딩하면, 디코딩 과정에 내부의 lock 으로 인해 전체 접근 횟수가 성능 병목 지점이 됨
        // 따라서, inputBuffer 의 크기를 16kb 로 확장하여, MediaCodec.decoder 의 전체 디코딩(inputBuffer -> decode -> outputBuffer) 횟수를 줄임
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

        extractor.selectTrack(audioTrackIndex) // 추출기의 track 을 오디오로 설정

        val decoder = MediaCodec.createDecoderByType(mime!!)

        val inputBufferChannel = Channel<Int>(Channel.UNLIMITED)
        val outputEventChannel = Channel<DecoderOutputEvent>(Channel.UNLIMITED)

        decoder.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                inputBufferChannel.trySend(index)
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo,
            ) {
                val copied = MediaCodec.BufferInfo().apply {
                    set(info.offset, info.size, info.presentationTimeUs, info.flags)
                }
                outputEventChannel.trySend(DecoderOutputEvent.BufferAvailable(index, copied))
            }

            override fun onOutputFormatChanged(codec: MediaCodec, fmt: MediaFormat) {
                outputEventChannel.trySend(DecoderOutputEvent.FormatChanged(fmt))
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                outputEventChannel.close(e)
                inputBufferChannel.close(e)
            }
        })

        decoder.configure(format, null, null, 0)
        decoder.start()

        coroutineScope {
            launch {
                for (inputBufferId in inputBufferChannel) {
                    ensureActive()

                    val inputBuffer = decoder.getInputBuffer(inputBufferId)!!
                    val capacity = inputBuffer.capacity()
                    var totalSize = 0
                    val sampleTime = extractor.sampleTime

                    while (true) {
                        val nextSampleSize = extractor.sampleSize
                        if (nextSampleSize < 0) {
                            break
                        }

                        if (totalSize > 0 && totalSize + nextSampleSize > capacity) {
                            break
                        }

                        val extractedSampleSize = extractor.readSampleData(inputBuffer, totalSize)

                        if (extractedSampleSize > 0) {
                            totalSize += extractedSampleSize
                        }

                        extractor.advance()
                    }

                    if (totalSize > 0)
                        decoder.queueInputBuffer(
                            inputBufferId,
                            0,
                            totalSize,
                            sampleTime,
                            0
                        )
                    else {
                        decoder.queueInputBuffer(
                            inputBufferId,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        break
                    }
                }
            }

            launch {
                var audioData: AudioSamplingBit? = null
                var currentPointer = 0

                for (event in outputEventChannel) {
                    ensureActive()

                    when (event) {
                        is DecoderOutputEvent.FormatChanged -> {
                            audioInfo = with(event.format) {
                                val sampleRate = getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                AudioInfo(
                                    sampleRate = sampleRate,
                                    channelCount = getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                                    samplingBit = with(
                                        getInteger(
                                            MediaFormat.KEY_PCM_ENCODING,
                                            AudioFormat.ENCODING_PCM_16BIT
                                        )
                                    ) audioFormat@{
                                        val sampleSize =
                                            sampleRate * 1 * AudioConfig.AUDIO_CHUNK_SECONDS
                                        AudioSamplingBit.create(this@audioFormat, sampleSize)
                                    },
                                    duration = format.getLong(MediaFormat.KEY_DURATION) / 1_000_000
                                ).apply {
                                    audioData = samplingBit
                                }
                            }
                        }

                        is DecoderOutputEvent.BufferAvailable -> {
                            val info = event.info
                            val outputBufferId = event.index

                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                decoder.releaseOutputBuffer(outputBufferId, false)
                                break
                            }

                            if (info.size == 0) {
                                decoder.releaseOutputBuffer(outputBufferId, false)
                                continue
                            }

                            val outputBuffer = decoder.getOutputBuffer(outputBufferId)

                            val bufferStart = System.nanoTime()

                            outputBuffer?.let { buffer ->
                                audioData?.let { audioSamplingBit ->
                                    while (buffer.hasRemaining()) {
                                        val sampleRemaining =
                                            buffer.remaining() / audioInfo!!.samplingBit.byte

                                        val canTake = when (audioSamplingBit) {
                                            is AudioSamplingBit.PCM8Bit -> {
                                                minOf(
                                                    sampleRemaining,
                                                    audioSamplingBit.data.size - currentPointer
                                                ).also { canTake ->
                                                    buffer.get(
                                                        audioSamplingBit.data,
                                                        currentPointer,
                                                        canTake
                                                    )
                                                }
                                            }

                                            is AudioSamplingBit.PCM16Bit -> {
                                                minOf(
                                                    sampleRemaining,
                                                    audioSamplingBit.data.size - currentPointer
                                                ).also { canTake ->
                                                    buffer.asShortBuffer()
                                                        .get(
                                                            audioSamplingBit.data,
                                                            currentPointer,
                                                            canTake
                                                        )
                                                    buffer.position(buffer.position() + canTake * audioSamplingBit.byte)
                                                }
                                            }

                                            is AudioSamplingBit.PCM32Bit -> {
                                                minOf(
                                                    sampleRemaining,
                                                    audioSamplingBit.data.size - currentPointer
                                                ).also { canTake ->
                                                    buffer.asFloatBuffer()
                                                        .get(
                                                            audioSamplingBit.data,
                                                            currentPointer,
                                                            canTake
                                                        )
                                                    buffer.position(buffer.position() + canTake * audioSamplingBit.byte)
                                                }
                                            }
                                        }

                                        currentPointer += canTake

                                        val isAudioDataFulled = when (audioSamplingBit) {
                                            is AudioSamplingBit.PCM16Bit -> currentPointer == audioSamplingBit.data.size
                                            is AudioSamplingBit.PCM32Bit -> currentPointer == audioSamplingBit.data.size
                                            is AudioSamplingBit.PCM8Bit -> currentPointer == audioSamplingBit.data.size
                                        }

                                        if (isAudioDataFulled) {
                                            val preprocessedAudio = audioPreProcessor.preProcess(audioInfo!!)
                                            extractedAudioChannel.send(preprocessedAudio)

                                            currentPointer = 0
                                        }
                                    }
                                }
                            }

                            decoder.releaseOutputBuffer(outputBufferId, false)
                        }
                    }
                }

                if (currentPointer != 0) {
                    audioData?.let { audioSamplingBit ->
                        val slicedByCurrentPointer = when (audioSamplingBit) {
                            is PCM8Bit -> PCM8Bit(audioSamplingBit.data.copyOf(currentPointer))
                            is PCM16Bit -> PCM16Bit(audioSamplingBit.data.copyOf(currentPointer))
                            is PCM32Bit -> PCM32Bit(audioSamplingBit.data.copyOf(currentPointer))
                        }
                        val preprocessedAudio = audioPreProcessor.preProcess(
                            audioInfo!!.copy(
                                samplingBit = slicedByCurrentPointer
                            )
                        )

                        extractedAudioChannel.send(preprocessedAudio)
                    }
                }
            }
        }.invokeOnCompletion { t ->
            inputBufferChannel.close()
            outputEventChannel.close()
            decoder.release()
            extractor.release()
            extractedAudioChannel.close()
        }

        Timber.tag("test").d("오디오 추출 종료")
    }
}

fun interface AudioPreProcessor<T> {
    suspend fun preProcess(audioInfo: AudioInfo): T
}

private sealed interface DecoderOutputEvent {
    data class FormatChanged(val format: MediaFormat) : DecoderOutputEvent
    data class BufferAvailable(val index: Int, val info: MediaCodec.BufferInfo) :
        DecoderOutputEvent
}

sealed interface AudioSamplingBit {
    val byte: Int

    class PCM8Bit(val data: ByteArray, override val byte: Int = 1) : AudioSamplingBit
    class PCM16Bit(val data: ShortArray, override val byte: Int = 2) : AudioSamplingBit
    class PCM32Bit(val data: FloatArray, override val byte: Int = 4) : AudioSamplingBit

    companion object {
        fun create(audioFormat: Int, sampleSize: Int): AudioSamplingBit {
            return when (audioFormat) {
                AudioFormat.ENCODING_PCM_8BIT -> PCM8Bit(ByteArray(sampleSize))
                AudioFormat.ENCODING_PCM_16BIT -> PCM16Bit(ShortArray(sampleSize / 2))
                AudioFormat.ENCODING_PCM_FLOAT -> PCM32Bit(FloatArray(sampleSize / 4))
                else -> throw IllegalStateException("Encoding Bytes [$audioFormat] is not supported")
            }
        }
    }
}

data class AudioInfo(
    val sampleRate: Int,
    val channelCount: Int,
    val samplingBit: AudioSamplingBit,
    val duration: Long,
)

@Parcelize
data class VideoItem(
    val uri: String,
    val title: String,
    val thumbnailAbsolutePath: String?,
    val date: String = "",
    val id: Long = SubtitleFileConfig.toSubtitleFileId(uri)
        ?: throw IllegalStateException("비디오 파일 contentUri [$uri] 가 올바른 형식이 아닙니다.")
) : Parcelable {
}
