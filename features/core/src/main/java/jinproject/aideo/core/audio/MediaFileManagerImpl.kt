package jinproject.aideo.core.audio

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jinproject.aideo.core.audio.AudioBuffer.MediaInfo
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.repository.impl.getSubtitleFileIdentifier
import jinproject.aideo.data.toSubtitleFileIdentifier
import jinproject.aideo.data.toThumbnailFileIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaFileManagerModule {

    @Binds
    abstract fun bindsVideoFileManager(
        mediaFileManagerImpl: MediaFileManagerImpl,
    ): MediaFileManager
}

interface MediaFileManager {
    suspend fun getVideoInfo(videoUriString: String): VideoItem?
    fun checkSubtitleFileExist(id: Long, languageCode: String): Int
    suspend fun extractAudioData(
        videoContentUri: Uri,
        produceAudio: suspend (audioData: ByteBuffer, bufferSize: Int, mediaInfo: MediaInfo) -> Unit
    ): MediaInfo
}

class MediaFileManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
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

    /**
     * 자막 파일이 존재하는지 확인하는 함수
     *
     * @return 언어코드와 일치하는 자막 파일이 있으면 1,
     * 언어코드와 일치하는 자막 파일은 없지만 다른 언어코드의 자막 파일이 있으면 0,
     * 어떠한 자막 파일도 없으면 -1
     */
    override fun checkSubtitleFileExist(id: Long, languageCode: String): Int {
        val isSubtitleExist = localFileDataSource.isFileExist(
            fileId = id,
            fileExtension = "".toSubtitleFileIdentifier()
        )

        if (isSubtitleExist) {
            val isSubtitleByLanguageExist =
                localFileDataSource.isFileExist(
                    fileIdentifier = getSubtitleFileIdentifier(
                        id = id,
                        languageCode = languageCode
                    )
                )

            return if (isSubtitleByLanguageExist)
                1
            else
                0
        }

        return -1
    }

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
    override suspend fun extractAudioData(
        videoContentUri: Uri,
        produceAudio: suspend (audioData: ByteBuffer, bufferSize: Int, mediaInfo: MediaInfo) -> Unit
    ): MediaInfo {
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
        val mediaInfo = MediaInfo(
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
            channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
        )

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
                                produceAudio(it, info.size, mediaInfo)
                            }
                        }

                        decoder.releaseOutputBuffer(outputBufferId, false)
                    }
                }
            }
        }


        Log.d("test", "전송된 포맷 mime: ${format.getString(MediaFormat.KEY_MIME)}")

        decoder.release()
        extractor.release()
        Log.d("test", "생산자 종료")

        return mediaInfo
    }
}

@Parcelize
data class VideoItem(
    val uri: String,
    val id: Long,
    val title: String,
    val thumbnailPath: String?,
) : Parcelable