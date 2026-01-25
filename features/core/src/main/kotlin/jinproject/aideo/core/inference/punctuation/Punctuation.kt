package jinproject.aideo.core.inference.punctuation

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflinePunctuation
import com.k2fsa.sherpa.onnx.OfflinePunctuationConfig
import com.k2fsa.sherpa.onnx.OfflinePunctuationModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.inference.AiModelConfig
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.core.utils.getPackAssetPath
import jinproject.aideo.data.BuildConfig
import jinproject.aideo.data.TranslationManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Punctuation @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private lateinit var punctuation: OfflinePunctuation
    var isInitialized: Boolean = false
        private set

    fun initialize() {
        if (isInitialized) {
            Log.d("test", "Already Onnx Punctuation has been initialized")
            return
        }

        val config = OfflinePunctuationConfig(
            model = OfflinePunctuationModelConfig(
                ctTransformer = "${context.getPackAssetPath(AiModelConfig.SPEECH_BASE_PACK)}/$CT_TRANSFORMER_MODEL_PATH",
                numThreads = 1,
                provider = "cpu",
                debug = BuildConfig.DEBUG
            )
        )

        punctuation = OfflinePunctuation(
            assetManager = null,
            config = config
        )

        isInitialized = true
    }

    fun release() {
        if (isInitialized) {
            punctuation.release()
            isInitialized = false
        }
    }

    private fun addPunctuation(text: String): String = punctuation.addPunctuation(text)

    fun addPunctuationOnSrt(srt: String): String {
        val contents = TranslationManager.extractSubtitleContent(srt)

        val punctuationContents = contents.split("@").joinToString("@") { txt ->
            addPunctuation(txt)
        }

        return TranslationManager.restoreMlKitTranslationToSrtFormat(
            originalSrtContent = srt,
            translatedContent = punctuationContents
        )
    }

    fun isAvailableLanguage(language: String) =
        language == LanguageCode.Chinese.code || language == LanguageCode.English.code

    companion object {
        const val CT_TRANSFORMER_MODEL_PATH = "${AiModelConfig.MODELS_ROOT_DIR}/punctuation.int8.onnx"
    }
}