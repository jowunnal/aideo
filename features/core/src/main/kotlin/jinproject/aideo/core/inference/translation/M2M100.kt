package jinproject.aideo.core.inference.translation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.inference.AiModelConfig
import jinproject.aideo.core.inference.AiModelConfig.MODELS_ROOT_DIR
import jinproject.aideo.core.inference.native.wrapper.M2M100Native
import jinproject.aideo.core.inference.translation.api.Translation
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.core.utils.copyAssetToInternalStorage
import jinproject.aideo.core.utils.getPackAssetPath
import jinproject.aideo.data.TranslationManager.getSubtitleFileIdentifier
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.datasource.local.LocalSettingDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.text.Charsets.UTF_8

@Singleton
class M2M100 @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val localFileDataSource: LocalFileDataSource,
    private val localSettingDataSource: LocalSettingDataSource,
) : Translation() {
    private var m2M100Native: M2M100Native? = null
    private var textBuffer: ByteBuffer? = null
    private var batchBuffer: ByteBuffer? = null

    override fun initialize() {
        if (m2M100Native != null) {
            return
        }

        m2M100Native = M2M100Native()
        textBuffer = ByteBuffer.allocateDirect(MAX_TEXT_BUFFER_SIZE)

        val isInitialized = m2M100Native!!.initialize()

        if (!isInitialized)
            return

        val vocabInternalPath = copyAssetToInternalStorage(path = VOCAB_PATH, context = context)
        val tokenizerConfigInternalPath =
            copyAssetToInternalStorage(path = TOKENIZER_CONFIG_PATH, context = context)

        val isModelLoaded = m2M100Native!!.loadModel(
            encoderPath = "${context.getPackAssetPath(AiModelConfig.TRANSLATION_BASE_PACK)}/$ENCODER_MODEL_PATH",
            decoderPath = "${context.getPackAssetPath(AiModelConfig.TRANSLATION_BASE_PACK)}/$DECODER_MODEL_PATH",
            decoderWithPastPath = "${context.getPackAssetPath(AiModelConfig.TRANSLATION_BASE_PACK)}/$DECODER_WITH_PAST_MODEL_PATH",
            spModelPath = "${context.getPackAssetPath(AiModelConfig.TRANSLATION_BASE_PACK)}/$SP_MODEL_PATH",
            vocabPath = vocabInternalPath,
            tokenizerConfigPath = tokenizerConfigInternalPath,
        )

        if(!isModelLoaded)
            m2M100Native = null
    }

    override suspend fun translateSubtitle(videoId: Long) {
        withContext(Dispatchers.Default) {
            val sourceLanguageISOCode =
                localFileDataSource.getOriginSubtitleLanguageCode(videoId)

            val targetLanguageISOCode = localSettingDataSource.getSubtitleLanguage().first()

            val srtContent = localFileDataSource.getFileContentList(
                getSubtitleFileIdentifier(
                    id = videoId,
                    languageCode = sourceLanguageISOCode
                )
            ) ?: throw IllegalStateException("content is null")

            val srcLang = LanguageCode.findByCode(sourceLanguageISOCode)!!
            val tgtLang = LanguageCode.findByCode(targetLanguageISOCode)!!

            val subtitleTexts = srtContent.filterIndexed { idx, _ -> (idx + 1) % 4 == 3 }

            val translatedTexts = translateBatch(
                texts = subtitleTexts,
                sourceLanguageCode = srcLang,
                targetLanguageCode = tgtLang
            )

            val translatedText = buildString {
                var translatedIdx = 0
                srtContent.forEachIndexed { idx, line ->
                    if ((idx + 1) % 4 == 3) {
                        append(translatedTexts[translatedIdx++])
                    } else {
                        append(line)
                    }
                    if (idx < srtContent.lastIndex) append('\n')
                }
            }

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
    }

    override suspend fun translate(
        text: String,
        srcLang: LanguageCode,
        tgtLang: LanguageCode,
        maxLength: Int,
    ): String {
        return translateWithBuffer(
            text = text.toByteArray(UTF_8),
            sourceLanguageCode = srcLang,
            targetLanguageCode = tgtLang,
            maxLength = maxLength
        )
    }

    /**
     * 배치 번역 (DirectByteBuffer를 통한 JNI zero-copy)
     *
     * 버퍼 포맷 (Length-Prefixed Binary):
     * [textCount: 4 bytes, big-endian int32]
     * [len1: 4 bytes][text1: len1 bytes (UTF-8)]
     * [len2: 4 bytes][text2: len2 bytes (UTF-8)]
     * ...
     *
     * @throws IllegalStateException : 번역 실패시
     */
    fun translateBatch(
        texts: List<String>,
        sourceLanguageCode: LanguageCode,
        targetLanguageCode: LanguageCode,
    ): Array<String> {
        if (m2M100Native == null)
            initialize()

        val encodedTexts = texts.map { it.toByteArray(UTF_8) }
        val totalLength = Int.SIZE_BYTES + encodedTexts.sumOf { Int.SIZE_BYTES + it.size }

        if (batchBuffer == null || batchBuffer!!.capacity() < totalLength)
            batchBuffer = ByteBuffer.allocateDirect(totalLength)

        val buffer = batchBuffer!!.apply {
            clear()
            putInt(texts.size)
            encodedTexts.forEach { bytes ->
                putInt(bytes.size)
                put(bytes)
            }
            flip()
        }

        return m2M100Native!!.translateBatch(
            textBuffer = buffer,
            srcLang = sourceLanguageCode.code,
            tgtLang = targetLanguageCode.code,
            maxLength = MAX_OUTPUT_LENGTH
        ) ?: throw IllegalStateException("Batch translation failed")
    }

    /**
     * ByteBuffer를 재사용하여 번역 (JNI 복사 오버헤드 감소)
     * @param text: UTF_8 인코딩
     * @throws IllegalStateException : 번역 실패시
     */
    fun translateWithBuffer(
        text: ByteArray,
        sourceLanguageCode: LanguageCode,
        targetLanguageCode: LanguageCode,
        maxLength: Int = MAX_OUTPUT_LENGTH,
    ): String {
        if (m2M100Native == null)
            initialize()

        if (textBuffer == null || textBuffer!!.capacity() < text.size)
            textBuffer = ByteBuffer.allocateDirect(text.size)

        val buffer = textBuffer!!.apply {
            clear() // pos = 0
            put(text) // pos = text.size
            flip() // pos = 0, limit = text.size
        }

        return m2M100Native!!.translateWithBuffer(
            textBuffer = buffer,
            textLength = text.size,
            srcLang = sourceLanguageCode.code,
            tgtLang = targetLanguageCode.code,
            maxLength = maxLength
        ) ?: throw IllegalStateException("Translation failed")
    }

    override fun release() {
        m2M100Native?.release()
        m2M100Native = null
        textBuffer = null
        batchBuffer = null
    }

    companion object {
        const val M2M100 = "m2m100"
        const val ENCODER_MODEL_PATH = "$MODELS_ROOT_DIR/${M2M100}_encoder.int8.onnx"
        const val DECODER_MODEL_PATH = "$MODELS_ROOT_DIR/${M2M100}_decoder.int8.onnx"
        const val DECODER_WITH_PAST_MODEL_PATH =
            "$MODELS_ROOT_DIR/${M2M100}_decoder_with_past.int8.onnx"
        const val SP_MODEL_PATH = "$MODELS_ROOT_DIR/${M2M100}_sentencepiece.bpe.model"
        const val VOCAB_PATH = "$MODELS_ROOT_DIR/${M2M100}_vocab.json"
        const val TOKENIZER_CONFIG_PATH = "$MODELS_ROOT_DIR/${M2M100}_tokenizer_config.json"

        private const val MAX_TEXT_BUFFER_SIZE = 1024
        private const val MAX_OUTPUT_LENGTH = 200
    }
}