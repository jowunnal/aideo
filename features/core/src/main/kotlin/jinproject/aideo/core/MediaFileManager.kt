package jinproject.aideo.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Parcelable
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.toSubtitleFileIdentifier
import jinproject.aideo.data.toThumbnailFileIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlin.math.roundToInt

@Module
@InstallIn(ViewModelComponent::class)
object VideoFileManagerModule {
    @Provides
    fun provideVideoFileManager(
        @ApplicationContext context: Context,
        localFileDataSource: LocalFileDataSource,
    ): MediaFileManager =
        MediaFileManager(context = context, localFileDataSource = localFileDataSource)
}

@OptIn(UnstableApi::class)
class MediaFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localFileDataSource: LocalFileDataSource,
) {

    suspend fun getVideoInfoList(videoUriString: String): VideoItem? =
        withContext(Dispatchers.IO) {
            val videoUri = videoUriString.toUri()
            var name: String? = null
            var duration: Long? = null
            val id = videoUriString.toUri().lastPathSegment?.toLong()
                ?: throw IllegalArgumentException("Invalid URI")

            val projection = arrayOf(
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
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
                    val durationIndex =
                        cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                    if (nameIndex != -1)
                        name = cursor.getString(nameIndex)
                    if (durationIndex != -1)
                        duration = cursor.getLong(durationIndex)
                }
            }

            if (duration != null && name != null) {
                context.contentResolver.takePersistableUriPermission(
                    videoUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                VideoItem(
                    uri = videoUri.toString(),
                    id = id,
                    title = name,
                    duration = duration,
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
    fun checkSubtitleFileExist(id: Long, languageCode: String): Int {
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
     * 비디오로 부터 음성을 추출하여 파일을 생성하고, 파일의 절대경로를 반환하는 함수
     *
     * 음성 파일이 이미 존재한다면, 기존의 음성 파일의 경로를 반환하고 그렇지 않다면, 새로 생성하여 경로를 반환한다.
     *
     * @param videoFileAbsolutePath 비디오 컨텐트 uri
     * @param wavIdentifier 출력될 wav 파일 식별자
     *
     * @return 파일의 identifier(이름 + 포맷)
     */
    fun extractAudioToWavWithResample(
        videoFileAbsolutePath: String,
        wavIdentifier: String,
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, videoFileAbsolutePath.toUri(), null)

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

        val originalSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val originalChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val targetSampleRate = 16000
        val targetChannels = 1
        val bitDepth = 16

        val pcmOut = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var isEOS = false
        val timeoutUs = 10000L

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
                val chunk = ByteArray(info.size)
                outputBuffer?.get(chunk)
                outputBuffer?.clear()
                pcmOut.write(chunk)
                decoder.releaseOutputBuffer(outputBufferId, false)
                outputBufferId = decoder.dequeueOutputBuffer(info, timeoutUs)
            }
        }

        decoder.stop()
        decoder.release()
        extractor.release()

        val pcmData = pcmOut.toByteArray()
        val shortBuffer =
            ByteBuffer.wrap(pcmData).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val shortArray = ShortArray(shortBuffer.limit())
        shortBuffer.get(shortArray)

        val monoShortArray = if (originalChannels == 2) {
            ShortArray(shortArray.size / 2) { i ->
                (((shortArray[2 * i].toInt() + shortArray[2 * i + 1].toInt()) / 2).toShort())
            }
        } else {
            shortArray
        }

        val resampledShortArray = if (originalSampleRate != targetSampleRate) {
            linearResample(monoShortArray, originalSampleRate, targetSampleRate)
        } else {
            monoShortArray
        }

        writeWavFile(
            context,
            wavIdentifier,
            resampledShortArray,
            targetSampleRate,
            targetChannels,
            bitDepth
        )
    }

    fun linearResample(input: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        val ratio = dstRate.toDouble() / srcRate
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

    fun writeWavFile(
        context: Context,
        filePath: String,
        pcmData: ShortArray,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int,
    ) {
        FileOutputStream(File(context.filesDir, filePath)).use { out ->
            val byteRate = sampleRate * channels * bitDepth / 8
            val totalDataLen = pcmData.size * 2 + 36
            val totalAudioLen = pcmData.size * 2

            out.write(
                byteArrayOf(
                    'R'.code.toByte(),
                    'I'.code.toByte(),
                    'F'.code.toByte(),
                    'F'.code.toByte()
                )
            )
            out.write(intToLittleEndian(totalDataLen))
            out.write(
                byteArrayOf(
                    'W'.code.toByte(),
                    'A'.code.toByte(),
                    'V'.code.toByte(),
                    'E'.code.toByte()
                )
            )
            out.write(
                byteArrayOf(
                    'f'.code.toByte(),
                    'm'.code.toByte(),
                    't'.code.toByte(),
                    ' '.code.toByte()
                )
            )
            out.write(intToLittleEndian(16))
            out.write(shortToLittleEndian(1))
            out.write(shortToLittleEndian(channels.toShort()))
            out.write(intToLittleEndian(sampleRate))
            out.write(intToLittleEndian(byteRate))
            out.write(shortToLittleEndian((channels * bitDepth / 8).toShort()))
            out.write(shortToLittleEndian(bitDepth.toShort()))
            out.write(
                byteArrayOf(
                    'd'.code.toByte(),
                    'a'.code.toByte(),
                    't'.code.toByte(),
                    'a'.code.toByte()
                )
            )
            out.write(intToLittleEndian(totalAudioLen))

            val buffer =
                ByteBuffer.allocate(pcmData.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buffer.asShortBuffer().put(pcmData)
            out.write(buffer.array())
        }
    }

    fun intToLittleEndian(value: Int): ByteArray = byteArrayOf(
        (value and 0xff).toByte(),
        ((value shr 8) and 0xff).toByte(),
        ((value shr 16) and 0xff).toByte(),
        ((value shr 24) and 0xff).toByte()
    )

    fun shortToLittleEndian(value: Short): ByteArray = byteArrayOf(
        (value.toInt() and 0xff).toByte(),
        ((value.toInt() shr 8) and 0xff).toByte()
    )
}

@Parcelize
data class VideoItem(
    val uri: String,
    val id: Long,
    val title: String,
    val duration: Long,
    val thumbnailPath: String?,
) : Parcelable