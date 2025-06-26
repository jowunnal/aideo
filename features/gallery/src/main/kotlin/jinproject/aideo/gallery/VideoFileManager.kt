package jinproject.aideo.gallery

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume

@Module
@InstallIn(ViewModelComponent::class)
object VideoFileManagerModule {
    @Provides
    fun provideVideoFileManager(
        @ApplicationContext context: Context,
        localFileDataSource: LocalFileDataSource,
    ): VideoFileManager =
        VideoFileManager(context = context, localFileDataSource = localFileDataSource)
}

@OptIn(UnstableApi::class)
class VideoFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localFileDataSource: LocalFileDataSource,
) {

    suspend fun extractAudioFromVideo(
        context: Context,
        videoUri: Uri,
        outputFileName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fileName = "$outputFileName.wav"
            val fileAbsolutePath = localFileDataSource.createFileAndGetAbsolutePath(fileName)

            if (fileAbsolutePath == null)
                return@withContext Result.failure(Exception("Audio Extraction file has been already exist"))

            val result = extractAudioWithTransformer(context, videoUri, fileAbsolutePath)

            if (result.isSuccess) {
                Result.success(fileAbsolutePath)
            } else {
                localFileDataSource.deleteFile(fileName)
                Result.failure(result.exceptionOrNull() ?: Exception("Audio extraction failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun extractAudioWithTransformer(
        context: Context,
        videoUri: Uri,
        fileAbsolutePath: String,
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            val mediaItem = MediaItem.fromUri(videoUri)

            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveVideo(true)
                .build()

            val transformer = Transformer.Builder(context)
                .setAudioMimeType(MimeTypes.AUDIO_WAV)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(
                            composition: Composition,
                            exportResult: ExportResult
                        ) {
                            if (continuation.isActive) {
                                continuation.resume(Result.success(Unit))
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(exportException))
                            }
                        }
                    }
                )
                .build()

            transformer.start(editedMediaItem, fileAbsolutePath)

            // 취소 시 Transformer 정리
            continuation.invokeOnCancellation {
                transformer.cancel()
            }

        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume(Result.failure(e))
            }
        }
    }

    suspend fun getVideoInfoList(videoUris: List<String>): List<VideoItem> =
        withContext(Dispatchers.IO) {
            mutableListOf<VideoItem>().apply {

                val selection = "_id IN (${videoUris.joinToString(",") { "?" }})"
                val selectionArgs = videoUris.toTypedArray()

                val projection = arrayOf(
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DURATION
                )

                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val nameIndex =
                            cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                        val durationIndex =
                            cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                        val id =
                            cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))

                        val name = cursor.getString(nameIndex)
                        val duration = cursor.getLong(durationIndex)
                        val uri = videoUris[cursor.position + 1]

                        add(
                            VideoItem(
                                uri = uri,
                                title = name,
                                duration = duration,
                                thumbnailPath = createThumbnailAndGetPath(
                                    uri = uri,
                                    fileName = id.toString()
                                )
                            )
                        )
                    }
                }
            }
        }

    private fun createThumbnailAndGetPath(
        uri: String,
        fileName: String
    ): String? {
        return MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri.toUri())

            val timeUs = 5 * 1_000_000L
            val bitmap =
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

            localFileDataSource.createFileAndWriteOnOutputStream(
                fileName = fileName,
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
}