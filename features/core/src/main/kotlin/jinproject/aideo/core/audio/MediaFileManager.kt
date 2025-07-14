package jinproject.aideo.core.audio

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import jinproject.aideo.data.repository.impl.getSubtitleFileIdentifier
import jinproject.aideo.data.toSubtitleFileIdentifier
import jinproject.aideo.data.toThumbnailFileIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

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

    suspend fun getVideoInfo(videoUriString: String): VideoItem? =
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
}

@Parcelize
data class VideoItem(
    val uri: String,
    val id: Long,
    val title: String,
    val thumbnailPath: String?,
) : Parcelable