package jinproject.aideo.core.runtime.impl.onnx

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.QnnConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.media.audio.AudioConfig
import jinproject.aideo.core.runtime.api.SpeechToText
import jinproject.aideo.data.BuildConfig
import jinproject.aideo.data.TranslationManager
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OnnxSTT

class OnnxSpeechToText @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeechToText() {
    private lateinit var recognizer: OfflineRecognizer
    override val transcribeResult = InferenceInfo(
        idx = 0,
        transcription = StringBuilder(),
        startTime = 0f,
        standardTime = 0f,
        endTime = 0f,
    )
    private lateinit var config: OfflineRecognizerConfig

    override fun initialize(r: InitRequirement) {
        if (isInitialized) {
            return
        }

        val requirements = (r as? OnnxRequirement)
            ?: throw IllegalArgumentException("[$r] is not OnnxRequirements")

        config = OfflineRecognizerConfig(
            featConfig = getFeatureConfig(AudioConfig.SAMPLE_RATE, 80),
        )

        // Setting Model Config
        when (requirements.availableModel) {
            is AvailableModel.Whisper -> setWhisperModelConfig(
                encoderPath = requirements.modelPath,
                decoderPath = requirements.availableModel.decoderPath,
                tokensPath = requirements.vocabPath,
                language = Locale.getDefault().language
            )

            is AvailableModel.SenseVoice -> setSenseVoiceModelConfig(
                modelPath = requirements.modelPath,
                language = "auto",
                tokensPath = requirements.vocabPath,
                isQnn = requirements.isQnn,
            )
        }

        recognizer = OfflineRecognizer(
            assetManager = if (requirements.isQnn) null else context.assets,
            config = config,
        )

        isInitialized = true
    }

    override fun release() {
        if (isInitialized) {
            recognizer.release()
            isInitialized = false
        }
    }

    override suspend fun transcribeByModel(audioData: FloatArray, language: String) {
        updateLanguageConfig(language)
        val stream = recognizer.createStream()

        try {
            stream.acceptWaveform(audioData, AudioConfig.SAMPLE_RATE)
            recognizer.decode(stream)

            recognizer.getResult(stream).let { result ->
                //Log.d("test", "recognized: ${result.text}")

                if (result.text.isNotEmpty())
                    with(transcribeResult) {
                        transcription.apply {
                            appendLine(this@with.idx++)
                            appendLine(
                                "${TranslationManager.formatSrtTime(startTime + standardTime)} --> ${
                                    TranslationManager.formatSrtTime(
                                        endTime + standardTime
                                    )
                                }"
                            )
                            appendLine(result.text)
                            appendLine()
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e("test", "Error while transcribing audio data", e)
        } finally {
            stream.release()
        }
    }

    override fun getResult(): String {
        return with(transcribeResult) {
            val result = transcription.toString().trim()
            transcription.clear()
            idx = 0
            startTime = 0f

            result
        }
    }

    fun setStandardTime(time: Float) {
        transcribeResult.standardTime = time
    }

    fun setTimes(start: Float, end: Float) {
        transcribeResult.apply {
            startTime = start
            endTime = end
        }
    }

    private fun setModelConfig(modelConfig: OfflineModelConfig) {
        config.modelConfig = modelConfig

        if (isInitialized)
            recognizer.setConfig(config)
    }

    fun setSenseVoiceModelConfig(
        modelPath: String,
        language: String,
        tokensPath: String,
        isQnn: Boolean = false,
    ) {
        val offlineModelConfig = if (isQnn) {
            OfflineRecognizer.prependAdspLibraryPath(context.applicationInfo.nativeLibraryDir)

            val qnnModelsDir = "qnn_models"
            val copiedModelPath = copyAssetToInternalStorage(
                path = "models/libmodel.so",
                context = context,
            )
            val copiedBinaryPath = copyAssetToInternalStorage(
                path = "models/model.bin",
                context = context,
            )
            val copiedTokensPath = copyAssetToInternalStorage(
                path = tokensPath,
                context = context
            )

            OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = copiedModelPath,
                    language = language,
                    useInverseTextNormalization = true,
                    qnnConfig = QnnConfig(
                        backendLib = "libQnnHtp.so",
                        systemLib = "libQnnSystem.so",
                        contextBinary = copiedBinaryPath,
                    )
                ),
                provider = "qnn",
                numThreads = 2,
                tokens = copiedTokensPath
            )
        } else
            OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = modelPath,
                    language = language,
                    useInverseTextNormalization = true,
                ),
                tokens = tokensPath,
                debug = BuildConfig.DEBUG,
            )

        setModelConfig(offlineModelConfig)
    }

    private fun copyAssetToInternalStorage(path: String, context: Context): String {
        val targetRoot = context.filesDir
        val outFile = File(targetRoot, path)

        if (!assetExists(context.assets, path = path)) {
            outFile.parentFile?.mkdirs()
            return outFile.absolutePath
        }

        if (outFile.exists()) {
            val assetSize = context.assets.open(path).use { it.available() }
            if (outFile.length() == assetSize.toLong()) {
                return "$targetRoot/$path"
            }
        }

        outFile.parentFile?.mkdirs()

        context.assets.open(path).use { input: InputStream ->
            FileOutputStream(outFile).use { output: OutputStream ->
                input.copyTo(output)
            }
        }

        return outFile.absolutePath
    }

    private fun assetExists(assetManager: AssetManager, path: String): Boolean {
        val dir = path.substringBeforeLast('/', "")
        val fileName = path.substringAfterLast('/')

        val files = assetManager.list(dir) ?: return false
        return files.contains(fileName)
    }

    fun setWhisperModelConfig(
        encoderPath: String,
        decoderPath: String,
        tokensPath: String,
        language: String,
    ) {
        setModelConfig(
            OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = encoderPath,
                    decoder = decoderPath,
                    language = language,
                ),
                tokens = tokensPath,
                debug = BuildConfig.DEBUG,
            )
        )
    }

    private fun updateLanguageConfig(language: String) {
        when (getAvailableModel()) {
            is AvailableModel.SenseVoice -> config.modelConfig.senseVoice.language = language
            is AvailableModel.Whisper -> config.modelConfig.whisper.language = language
        }

        recognizer.setConfig(config)
    }

    private fun getAvailableModel(): AvailableModel {
        return if (config.modelConfig.senseVoice.model.isEmpty())
            AvailableModel.Whisper(decoderPath = config.modelConfig.whisper.decoder)
        else
            AvailableModel.SenseVoice
    }

    class InferenceInfo(
        var idx: Int,
        override val transcription: StringBuilder,
        var standardTime: Float,
        var startTime: Float,
        var endTime: Float,
    ) : TranscribeResult()

    sealed class AvailableModel {
        data object SenseVoice : AvailableModel()
        data class Whisper(val decoderPath: String) : AvailableModel()
    }

    class OnnxRequirement(
        modelPath: String,
        vocabPath: String,
        val availableModel: AvailableModel,
        val isQnn: Boolean = false,
    ) : InitRequirement(modelPath = modelPath, vocabPath = vocabPath)
}