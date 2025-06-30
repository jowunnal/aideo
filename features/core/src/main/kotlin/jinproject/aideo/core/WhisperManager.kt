package jinproject.aideo.core


import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.whispercpp.whisper.WhisperContext
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

@Module
@InstallIn(SingletonComponent::class)
object WhisperManagerModule {
    @Provides
    fun provideWhisperManager(
        @ApplicationContext context: Context,
        localFileDataSource: LocalFileDataSource,
    ): WhisperManager {
        return WhisperManager(
            context = context,
            localFileDataSource = localFileDataSource,
        )
    }
}

class WhisperManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localFileDataSource: LocalFileDataSource,
) {
    private val modelsPath = File(context.filesDir, "models")
    lateinit var whisperContext: WhisperContext

    var isReady: Boolean by mutableStateOf(false)
        private set

    suspend fun load() {
        copyWeightBinaryData()
        loadBaseModel()
        isReady = true
    }

    private suspend fun copyWeightBinaryData() = withContext(Dispatchers.IO) {
        val assetPath = WEIGHT_FILE_PATH

        if (!modelsPath.exists())
            modelsPath.mkdirs()

        val destination = File(modelsPath, WEIGHT_FILE_NAME)
        context.assets.open(assetPath).use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private suspend fun loadBaseModel() = withContext(Dispatchers.IO) {
        val models = context.assets.list("models/")
        if (models != null && !::whisperContext.isInitialized) {
            whisperContext = WhisperContext.createContextFromAsset(
                context.assets,
                WEIGHT_FILE_PATH,
            )
        }
    }

    /**
     * 오디오 파일을 텍스트로 변환하여 자막 파일(Srt) 생성
     *
     * @param audioFileAbsolutePath : 16 비트 PCM WAV 오디오 파일
     */
    suspend fun transcribeAudio(audioFileAbsolutePath: String, getLanguage: suspend (String) -> String,) {
        if (!isReady) {
            return
        }

        try {
            val audioFile = localFileDataSource.getFileReference(audioFileAbsolutePath) ?: return
            val data = decodeWaveFile(audioFile)
            val transcribedText = whisperContext.transcribeData(data)
            val srtContent = convertToSrt(transcribedText)
            val languageCode = getLanguage(srtContent.substring(0,50))

            localFileDataSource.createFileAndWriteOnOutputStream(
                fileIdentifier = "${audioFile.name}_$languageCode".toSubtitleFileIdentifier(),
                writeContentOnFile = { outputStream ->
                    runCatching {
                        outputStream.write(srtContent.toByteArray())
                    }.map {
                        true
                    }.getOrElse {
                        false
                    }
                }
            )
        } catch (e: Exception) {

        }
    }

    /**
     * 16 비트 WAV 오디오 파일을 정규화된 FloatArray 로 변환하는 함수
     */
    private fun decodeWaveFile(file: File): FloatArray {
        val baos = ByteArrayOutputStream()
        file.inputStream().use { it.copyTo(baos) }
        val buffer = ByteBuffer.wrap(baos.toByteArray())
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val channel = buffer.getShort(22).toInt()
        buffer.position(44)
        val shortBuffer = buffer.asShortBuffer()
        val shortArray = ShortArray(shortBuffer.limit())
        shortBuffer.get(shortArray)
        return FloatArray(shortArray.size / channel) { index ->
            when (channel) {
                1 -> (shortArray[index] / 32767.0f).coerceIn(-1f..1f)
                else -> ((shortArray[2 * index] + shortArray[2 * index + 1]) / 32767.0f / 2.0f).coerceIn(
                    -1f..1f
                )
            }
        }
    }

    private fun convertToSrt(transcribeOutput: String): String {
        val lines = transcribeOutput.trim().lines()
        val srtBuilder = StringBuilder()
        val regex =
            Regex("\\[(\\d{2}:\\d{2}:\\d{2}\\.\\d{3}) --> (\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\]: (.*)")
        var index = 1

        for (line in lines) {
            val matchResult = regex.matchEntire(line)
            if (matchResult != null) {
                val (startTime, endTime, text) = matchResult.destructured
                val start = startTime.replace('.', ',')
                val end = endTime.replace('.', ',')

                srtBuilder.appendLine(index)
                srtBuilder.appendLine("$start --> $end")
                srtBuilder.appendLine(text)
                srtBuilder.appendLine()
                index++
            }
        }

        return srtBuilder.toString()
    }

    suspend fun release() {
        whisperContext.release()
        isReady = false
    }

    companion object {
        const val WEIGHT_FILE_NAME = "ggml-base.bin"
        const val WEIGHT_FILE_PATH = "models/$WEIGHT_FILE_NAME"
    }
}