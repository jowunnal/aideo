package jinproject.aideo.data.datasource.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class LocalFileDataSource @Inject constructor(@ApplicationContext private val context: Context) {
    /**
     * 로컬 경로에 파일 생성 후, 절대 경로를 반환하는 함수
     *
     * @param fileName 생성할 파일의 이름
     * @param writeContentOnFile 파일에 데이터를 쓰는 함수
     *
     * @return 파일의 content Uri or 생성에 실패한 경우 null
     */
    fun createFileAndWriteOnOutputStream(
        fileName: String,
        writeContentOnFile: (FileOutputStream) -> Boolean,
    ): String? {
        val file = File(context.filesDir, fileName)

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
     * @param fileName 생성할 파일의 이름
     *
     * @return 파일의 content Uri or 파일이 이미 존재하는 경우 null
     */
    fun createFileAndGetAbsolutePath(
        fileName: String,
    ): String? {
        val file = File(context.filesDir, fileName)

        return if (file.exists()) null else file.absolutePath
    }

    fun deleteFile(fileName: String): Boolean {
        val file = File(context.filesDir, fileName)

        return file.delete()
    }

    /**
     * 파일의 내용을 가져오는 함수
     *
     * @param fileAbsolutePath : 파일의 절대 경로
     *
     * @return 한줄씩 읽은 List<String>
     */
    fun getFileContent(fileAbsolutePath: String): List<String>? {
        val file = File(fileAbsolutePath)

        return if (file.exists()) file.readLines() else null
    }
}