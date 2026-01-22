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
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jinproject.aideo.core.media.audio.AudioConfig
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.toThumbnailFileIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.IOException
import java.nio.ByteOrder
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
            val id = videoUriString.toUri().lastPathSegment?.toLong()
                ?: throw IllegalArgumentException("Invalid URI")

            val projection = arrayOf(
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED,
            )

            context.contentResolver.query(
                videoUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex =
                        cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)

                    if (nameIndex != -1)
                        name = cursor.getString(nameIndex)
                }
            }

            if (name != null) {
                context.contentResolver.takePersistableUriPermission(
                    videoUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                VideoItem(
                    uri = videoUri.toString(),
                    id = id,
                    title = name,
                    thumbnailPath = createThumbnailAndGetPath(
                        uri = videoUri.toString(),
                        fileName = id.toString().toThumbnailFileIdentifier()
                    )
                )
            } else
                null
        }

    private fun createThumbnailAndGetPath(
        uri: String,
        fileName: String,
    ): String? {
        return MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri.toUri())

            val timeUs = 5 * 1_000_000L
            val bitmap =
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

            localFileDataSource.createFileAndWriteOnOutputStream(
                fileIdentifier = fileName,
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

    var mediaInfo: MediaInfo? = null
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

        extractor.selectTrack(audioTrackIndex) // 추출기의 track 을 오디오로 설정

        val decoder = MediaCodec.createDecoderByType(mime!!)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val timeoutUs = 5000L
        val decoderBufferInfo = MediaCodec.BufferInfo()

        coroutineScope {
            launch {
                while (true) {
                    val inputBufferId = decoder.dequeueInputBuffer(timeoutUs)
                    if (inputBufferId >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferId)
                        val sampleSize = extractor.readSampleData(inputBuffer!!, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputBufferId, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            break
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
                var audioData: ByteArray? = null
                var currentPointer = 0

                while (true) {
                    val outputBufferId = decoder.dequeueOutputBuffer(decoderBufferInfo, timeoutUs)

                    if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        mediaInfo = with(decoder.outputFormat) {
                            MediaInfo(
                                sampleRate = getInteger(MediaFormat.KEY_SAMPLE_RATE),
                                channelCount = getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                                encodingBytes = run {
                                    val pcmEncodingFormat = getInteger(
                                        MediaFormat.KEY_PCM_ENCODING,
                                        AudioFormat.ENCODING_PCM_16BIT
                                    )

                                    when (pcmEncodingFormat) {
                                        AudioFormat.ENCODING_PCM_8BIT -> 1
                                        AudioFormat.ENCODING_PCM_16BIT -> 2
                                        AudioFormat.ENCODING_PCM_FLOAT -> 4
                                        else -> throw IllegalStateException("Encoding Type [${pcmEncodingFormat}] is not supported")
                                    }
                                },
                                duration = format.getLong(MediaFormat.KEY_DURATION) / 1_000_000
                            ).apply {
                                audioData =
                                    ByteArray(sampleRate * 1 * encodingBytes * AudioConfig.AUDIO_CHUNK_SECONDS) // 30 초 분량의 오디오 단위 처리
                            }
                        }
                    } else if (outputBufferId >= 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputBufferId)

                        if (decoderBufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                            break
                        } else if (decoderBufferInfo.size != 0) {
                            outputBuffer?.let { buffer ->
                                buffer.apply {
                                    order(ByteOrder.nativeOrder())
                                }

                                audioData?.let {
                                    val remainingBufferSize = buffer.remaining()
                                    if (currentPointer + remainingBufferSize <= audioData.size) {
                                        repeat(remainingBufferSize) {
                                            audioData[currentPointer++] = buffer.get()
                                        }
                                    } else {
                                        repeat(audioData.size - currentPointer) {
                                            audioData[currentPointer++] = buffer.get()
                                        }

                                        extractedAudioChannel.send(
                                            audioPreProcessor.preProcess(
                                                ExtractedAudioInfo(
                                                    audioData = audioData,
                                                    mediaInfo = mediaInfo!! //TODO 멀티 스레드로 AndroidMediaFileManager 인스턴스 접근하여, MediaInfo 값을 변경하면 문제발생
                                                )
                                            )
                                        )

                                        currentPointer = 0
                                        repeat(audioData.size) { idx ->
                                            audioData[idx] = 0x00.toByte()
                                        }

                                        if (buffer.hasRemaining()) {
                                            repeat(buffer.remaining()) {
                                                audioData[currentPointer++] = buffer.get()
                                            }
                                        }
                                    }
                                }
                            }

                            decoder.releaseOutputBuffer(outputBufferId, false)
                        }
                    }
                }

                if (currentPointer != 0 && audioData != null) {
                    extractedAudioChannel.send(
                        audioPreProcessor.preProcess(
                            ExtractedAudioInfo(
                                audioData = audioData.sliceArray(0 until currentPointer),
                                mediaInfo = mediaInfo!!
                            )
                        )
                    )
                }
            }
        }

        decoder.release()
        extractor.release()
        extractedAudioChannel.close()
        Log.d("test", "오디오 추출 종료")
    }
}

fun interface AudioPreProcessor<T> {
    suspend fun preProcess(audioInfo: ExtractedAudioInfo): T
}

class ExtractedAudioInfo(
    var audioData: ByteArray,
    var mediaInfo: MediaInfo,
)

data class MediaInfo(
    val sampleRate: Int,
    val channelCount: Int,
    val encodingBytes: Int,
    val duration: Long,
)

@Parcelize
data class VideoItem(
    val uri: String,
    val id: Long,
    val title: String,
    val thumbnailPath: String?,
) : Parcelable