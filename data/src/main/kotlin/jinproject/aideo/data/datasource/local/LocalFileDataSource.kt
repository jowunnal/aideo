package jinproject.aideo.data.datasource.local

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import javax.inject.Inject

class LocalFileDataSource @Inject constructor(@ApplicationContext private val context: Context) {
    /**
     * 로컬 경로에 파일 생성 후, 절대 경로를 반환하는 함수
     *
     * @param fileIdentifier 생성할 파일의 식별자(이름+확장자)
     * @param writeContentOnFile 파일에 데이터를 쓰는 함수
     *
     * @return 파일의 content Uri or 생성에 실패한 경우 null
     */
    fun createFileAndWriteOnOutputStream(
        fileIdentifier: String,
        writeContentOnFile: (FileOutputStream) -> Boolean,
    ): String? {
        val file = File(context.filesDir, fileIdentifier)

        if (file.exists())
            return file.absolutePath

        FileOutputStream(file).use { outputStream ->
            val compressedResult = writeContentOnFile(outputStream)

            if (compressedResult == true) {
                return file.absolutePath
            }
        }

        return null
    }

    /**
     * 로컬 경로에 파일 생성 후, 절대 경로를 반환하는 함수
     *
     * @param fileIdentifier 생성할 파일의 식별자(이름+확장자)
     *
     * @return 파일의 absolutePath
     */
    fun createFileAndGetAbsolutePath(
        fileIdentifier: String,
    ): String {
        val file = File(context.filesDir, fileIdentifier)

        if (!file.exists())
            file.createNewFile()

        return file.absolutePath
    }

    fun deleteFile(fileIdentifier: String): Boolean {
        val file = File(context.filesDir, fileIdentifier)

        return file.delete()
    }

    /**
     * 파일의 내용을 가져오는 함수
     *
     * @param fileIdentifier : 파일의 identifier(이름 + 포맷)
     *
     * @return 한줄씩 읽은 List<String>
     */
    fun getFileContent(fileIdentifier: String): List<String>? {
        val file = File(context.filesDir, fileIdentifier)

        return if (file.exists()) file.readLines() else null
    }

    fun isFileExist(fileIdentifier: String): Boolean {
        val file = File(context.filesDir, fileIdentifier)

        return file.exists()
    }

    fun isFileExist(fileId: Long, fileExtension: String): Boolean {
        val dir = context.filesDir

        val matchedFiles = dir.listFiles()
            ?.any { it.name.startsWith(fileId.toString()) && it.name.endsWith(fileExtension) } ?: false

        return matchedFiles
    }

    fun getFileReference(fileIdentifier: String): File? {
        val file = File(context.filesDir, fileIdentifier)

        return if (file.exists()) file else null
    }

    fun getFileAbsolutePath(fileIdentifier: String): String? {
        val file = File(context.filesDir, fileIdentifier)

        return if (file.exists()) file.absolutePath else null
    }

    fun replaceFileContent(newContent: String, fileIdentifier: String) {
        val file = File(context.filesDir, fileIdentifier)

        FileWriter(file, false).use {
            it.write(newContent)
        }
    }
}