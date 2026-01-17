package jinproject.aideo.core.runtime.impl.onnx

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.runtime.impl.onnx.OnnxModelConfig.MODELS_ROOT_DIR
import jinproject.aideo.core.runtime.impl.onnx.wrapper.T5Native
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.core.utils.copyAssetToInternalStorage
import jinproject.aideo.data.TranslationManager.getSubtitleFileIdentifier
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlanT5 @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val localFileDataSource: LocalFileDataSource,
    private val localPlayerDataSource: LocalPlayerDataSource,
) {
    private var t5Native: T5Native? = null
    var strBuilder: StringBuilder? = null
        private set

    fun initialize(): Boolean {
        if (t5Native != null) {
            return true
        }

        t5Native = T5Native()
        strBuilder = StringBuilder()

        val isInitialized = t5Native!!.initialize()

        if (!isInitialized)
            return false

        val modelInternalPath = copyAssetToInternalStorage(path = MODEL_PATH, context = context)
        val isModelLoaded = t5Native!!.loadModel(modelInternalPath)

        val tokenInternalPath = copyAssetToInternalStorage(path = TOKEN_PATH, context = context)
        val isTokenLoaded = t5Native!!.loadTokenizer(tokenInternalPath)

        return isModelLoaded && isTokenLoaded
    }

    suspend fun translateSubtitle(videoId: Long) = withContext(Dispatchers.IO) {
        val sourceLanguageISOCode =
            localFileDataSource.getOriginSubtitleLanguageCode(videoId)
        val targetLanguageISOCode = localPlayerDataSource.getSubtitleLanguage().first()

        val translatedText = translate(
            sourceLanguageCode = LanguageCode.findByName(sourceLanguageISOCode),
            targetLanguageCode = LanguageCode.findByName(targetLanguageISOCode)
        )

        localFileDataSource.createFileAndWriteOnOutputStream(
            fileIdentifier = getSubtitleFileIdentifier(
                id = videoId,
                languageCode = targetLanguageISOCode
            ),
            writeContentOnFile = { outputStream ->
                runCatching {
                    outputStream.write(translatedText.toByteArray())
                }.map {
                    true
                }.getOrElse {
                    false
                }
            }
        )
    }

    /**
     * @throws IllegalStateException : 번역 실패시
     */
    fun translate(
        sourceLanguageCode: LanguageCode,
        targetLanguageCode: LanguageCode,
    ): String {
        if (t5Native == null)
            initialize()

        strBuilder?.apply {
            insert(0, "Translate from ${sourceLanguageCode.name} to ${targetLanguageCode.name}:")
        }

        return t5Native!!.generateText(
            inputText = strBuilder.toString(),
            maxLength = 128
        ).also {
            strBuilder?.clear()
        } ?: throw IllegalStateException("Translation failed")
    }

    fun release() {
        t5Native?.release()
    }

    companion object {
        const val MODEL_PATH = "$MODELS_ROOT_DIR/flan_t5.int8.onnx"
        const val TOKEN_PATH = "$MODELS_ROOT_DIR/flan_t5_token.json"
    }
}