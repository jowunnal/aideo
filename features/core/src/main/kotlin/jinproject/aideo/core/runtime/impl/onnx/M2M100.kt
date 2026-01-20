package jinproject.aideo.core.runtime.impl.onnx

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.runtime.impl.onnx.OnnxModelConfig.MODELS_ROOT_DIR
import jinproject.aideo.core.runtime.impl.onnx.wrapper.M2M100Native
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.core.utils.copyAssetToInternalStorage
import jinproject.aideo.data.TranslationManager
import jinproject.aideo.data.TranslationManager.getSubtitleFileIdentifier
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import jinproject.aideo.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.sequences.joinToString

@Singleton
class M2M100 @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val localFileDataSource: LocalFileDataSource,
    private val localPlayerDataSource: LocalPlayerDataSource,
) {
    private var m2M100Native: M2M100Native? = null
    var strBuilder: StringBuilder? = null
        private set

    fun initialize(): Boolean {
        if (m2M100Native != null) {
            return true
        }

        m2M100Native = M2M100Native()
        strBuilder = StringBuilder()

        val isInitialized = m2M100Native!!.initialize()

        if (!isInitialized)
            return false

        val encoderInternalPath =
            copyAssetToInternalStorage(path = ENCODER_MODEL_PATH, context = context)
        val decoderInternalPath =
            copyAssetToInternalStorage(path = DECODER_MODEL_PATH, context = context)
        val decoderWithPastInternalPath =
            copyAssetToInternalStorage(path = DECODER_WITH_PAST_MODEL_PATH, context = context)
        val spModelInternalPath = copyAssetToInternalStorage(path = SP_MODEL_PATH, context = context)
        val vocabInternalPath = copyAssetToInternalStorage(path = VOCAB_PATH, context = context)
        val tokenizerConfigInternalPath =
            copyAssetToInternalStorage(path = TOKENIZER_CONFIG_PATH, context = context)

        val isModelLoaded = m2M100Native!!.loadModel(
            encoderPath = encoderInternalPath,
            decoderPath = decoderInternalPath,
            decoderWithPastPath = decoderWithPastInternalPath,
            spModelPath = spModelInternalPath,
            vocabPath = vocabInternalPath,
            tokenizerConfigPath = tokenizerConfigInternalPath,
        )

        return isModelLoaded
    }

    suspend fun translateSubtitle(videoId: Long) = withContext(Dispatchers.Default) {
        val sourceLanguageISOCode =
            localFileDataSource.getOriginSubtitleLanguageCode(videoId)
        val targetLanguageISOCode = localPlayerDataSource.getSubtitleLanguage().first()

        val srtContent = localFileDataSource.getFileContent(
            getSubtitleFileIdentifier(
                id = videoId,
                languageCode = sourceLanguageISOCode
            )
        )?.joinToString("\n")
            ?: throw MediaRepository.TranscriptionException.TranscriptionFailed(
                message = "content is null",
                cause = null
            )

        val extracted = TranslationManager.splitSubtitleToList(srtContent).joinToString("@") {
            translate(
                text = it,
                sourceLanguageCode = LanguageCode.findByCode(sourceLanguageISOCode)!!,
                targetLanguageCode = LanguageCode.findByCode(targetLanguageISOCode)!!
            )
        }

        val translatedText = TranslationManager.restoreMlKitTranslationToSrtFormat(
            originalSrtContent = srtContent,
            translatedContent = extracted
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
        text: String,
        sourceLanguageCode: LanguageCode,
        targetLanguageCode: LanguageCode,
    ): String {
        if (m2M100Native == null)
            initialize()

        return m2M100Native!!.translate(
            text = text,
            srcLang = sourceLanguageCode.code,
            tgtLang = targetLanguageCode.code,
            maxLength = 200
        ).also {
            strBuilder?.clear()
        } ?: throw IllegalStateException("Translation failed")
    }

    fun release() {
        m2M100Native?.release()
    }

    companion object {
        const val M2M100 = "m2m100"
        const val ENCODER_MODEL_PATH = "$MODELS_ROOT_DIR/${M2M100}_encoder.int8.onnx"
        const val DECODER_MODEL_PATH = "$MODELS_ROOT_DIR/${M2M100}_decoder.int8.onnx"
        const val DECODER_WITH_PAST_MODEL_PATH = "$MODELS_ROOT_DIR/${M2M100}_decoder_with_past.int8.onnx"
        const val SP_MODEL_PATH = "$MODELS_ROOT_DIR/${M2M100}_sentencepiece.bpe.model"
        const val VOCAB_PATH = "$MODELS_ROOT_DIR/${M2M100}_vocab.json"
        const val TOKENIZER_CONFIG_PATH = "$MODELS_ROOT_DIR/${M2M100}_tokenizer_config.json"
    }
}