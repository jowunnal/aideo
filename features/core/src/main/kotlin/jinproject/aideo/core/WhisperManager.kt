package jinproject.aideo.core


import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.whispertflite.engine.WhisperEngine
import com.whispertflite.engine.WhisperEngineNative
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jinproject.aideo.data.TranslationManager
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.toAudioFileWAVIdentifier
import jinproject.aideo.data.repository.impl.getSubtitleFileIdentifier
import kotlinx.coroutines.Dispatchers
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
    private val whisperContext: WhisperEngine by lazy { WhisperEngineNative() }

    var isReady: Boolean by mutableStateOf(false)
        private set

    suspend fun load() {
        copyWeightBinaryData()
        loadBaseModel()
        isReady = true
    }

    private suspend fun copyWeightBinaryData() = withContext(Dispatchers.IO) {
        val models = context.assets.list("models/")

        if (models == null)
            return@withContext

        val modelsPath = File(context.filesDir, "models")

        if (!modelsPath.exists())
            modelsPath.mkdirs()

        val destination = File(modelsPath, WEIGHT_FILE_NAME)
        context.assets.open(WEIGHT_FILE_PATH).use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val vocab = File(modelsPath, VOCAB_FILE_NAME)
        context.assets.open(VOCAB_FILE_PATH).use { input ->
            vocab.outputStream().use { output ->
                input.copyTo(output)
            }
        }


    }

    private suspend fun loadBaseModel() = withContext(Dispatchers.IO) {
        val modelsPath = File(context.filesDir, WEIGHT_FILE_PATH).absolutePath
        val vocabPath = File(context.filesDir, VOCAB_FILE_PATH).absolutePath

        whisperContext.initialize(modelsPath, vocabPath, true)
    }

    /**
     * 오디오 파일을 텍스트로 변환하여 자막 파일(Srt) 생성
     *
     * @param audioFileWavIdentifier : 16 비트 PCM WAV 오디오 파일
     */
    suspend fun transcribeAudio(
        audioFileId: Long,
    ) {
        if (!whisperContext.isInitialized) {
            return
        }

        val audioFileWavIdentifier = toAudioFileWAVIdentifier(id = audioFileId)
        val audioFile = localFileDataSource.getFileReference(audioFileWavIdentifier) ?: return
        val transcribedText = whisperContext.transcribeFile(audioFile.absolutePath)
        Log.d("test", "translatedText : $transcribedText")
        val languageCode = TranslationManager.detectLanguage(transcribedText)
        Log.d("test", "languageCode : $languageCode")

        localFileDataSource.createFileAndWriteOnOutputStream(
            fileIdentifier = getSubtitleFileIdentifier(
                id = audioFileId,
                languageCode = languageCode
            ),
            writeContentOnFile = { outputStream ->
                runCatching {
                    outputStream.bufferedWriter().use { writer ->
                        writer.write(transcribedText)
                    }
                }.map {
                    true
                }.getOrElse {
                    false
                }
            }
        )
    }

    /**
     * 16 비트 WAV 오디오 파일을 정규화된 FloatArray 로 변환하는 함수
     */
    fun decodeWaveFile(file: File): FloatArray {
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

    fun release() {
        whisperContext.deinitialize()
        isReady = false
    }

    companion object {
        const val WEIGHT_FILE_NAME = "whisper-tiny.tflite"
        const val WEIGHT_FILE_PATH = "models/$WEIGHT_FILE_NAME"
        const val VOCAB_FILE_NAME = "filters_vocab_multilingual.bin"
        const val VOCAB_FILE_PATH = "models/$VOCAB_FILE_NAME"
    }
}