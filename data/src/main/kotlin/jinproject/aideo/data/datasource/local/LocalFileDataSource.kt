package jinproject.aideo.data.datasource.local

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.data.FileIdentifier
import jinproject.aideo.data.SubtitleFileConfig
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class LocalFileDataSource @Inject constructor(@param:ApplicationContext private val context: Context) {
    /**
     * 로컬 경로에 파일 생성 후, 절대 경로를 반환하는 함수
     *
     * @param fileIdentifier 생성할 파일의 식별자(이름+확장자)
     * @param writeContentOnFile 파일에 데이터를 쓰는 함수
     *
     * @return 파일의 content Uri or 생성에 실패한 경우 null
     */
    fun createFileAndWriteOnOutputStream(
        fileIdentifier: FileIdentifier,
        writeContentOnFile: (FileOutputStream) -> Boolean,
    ): String? {
        val file = File(context.filesDir, fileIdentifier.identifier)

        FileOutputStream(file).use { outputStream ->
            val compressedResult = writeContentOnFile(outputStream)

            if (compressedResult) {
                return file.absolutePath
            }
        }

        return null
    }

    /**
     * 파일의 내용을 가져오는 함수
     *
     * @param fileIdentifier : 파일의 identifier(이름 + 포맷)
     *
     * @return 한줄씩 읽은 List<String>
     */
    fun getFileContentList(fileIdentifier: FileIdentifier): List<String>? {
        val file = File(context.filesDir, fileIdentifier.identifier)

        return if (file.exists()) file.readLines() else null
    }

    /**
     * 존재하는 자막 파일의 언어 코드를 반환하는 함수
     *
     * @param fileId 자막 파일의 비디오 아이템 id
     *
     * @return 언어 코드 ISO 값
     */
    fun getOriginSubtitleLanguageCodeOrNull(fileId: Long): String? {
        val matchedFiles = getSubtitleFiles(fileId)

        if (matchedFiles.isEmpty())
            return null

        return matchedFiles.first().nameWithoutExtension.split("_")[1]
    }

    private fun getSubtitleFiles(fileId: Long): List<File> {
        val file = context.filesDir

        val matchedFiles = file.listFiles()
            ?.filter { it.name.startsWith("${fileId}_") && it.name.endsWith(SubtitleFileConfig.SUBTITLE_EXTENSION) }


        if (matchedFiles.isNullOrEmpty())
            return emptyList()

        return matchedFiles
    }

    fun isFileExist(fileIdentifier: FileIdentifier): Boolean {
        val file = File(context.filesDir, fileIdentifier.identifier)

        return file.exists()
    }

    fun isFileExist(fileId: Long, fileExtension: String): Boolean {
        val dir = context.filesDir

        val matchedFiles = dir.listFiles()
            ?.any { it.name.startsWith(fileId.toString()) && it.name.endsWith(fileExtension) } == true

        return matchedFiles
    }

    /**
     * 존재하는 자막 파일을 제거
     *
     * @param fileId 비디오 파일 id
     */
    fun removeAllSubtitles(fileId: Long) {
        getSubtitleFiles(fileId).forEach { file ->
            file.delete()
        }
    }

    fun getFileUri(fileId: Long, languageCode: String): Uri {
        return File(
            context.filesDir,
            SubtitleFileConfig.toSubtitleFileIdentifier(
                id = fileId,
                languageCode = languageCode,
            ).identifier
        ).toUri()
    }
}