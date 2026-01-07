package jinproject.aideo.core.runtime.impl.executorch

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.inference.whisper.VocabUtils
import jinproject.aideo.core.runtime.api.SpeechToText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Qualifier
import kotlin.text.Charsets.UTF_8

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ExecutorchSTT

/**
 * Executorch 런타임을 활용하여 [.pte] 포맷의 모델의 추론을 담당
 */
class ExecutorchSpeechToText @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vocabUtils: VocabUtils,
    modelPath: String,
    vocabPath: String,
) : SpeechToText(modelPath = modelPath, vocabPath = vocabPath) {
    lateinit var module: Module
    override val transcribeResult =
        InferenceInfo(index = 0, lastSeconds = 0f, transcription = StringBuilder())

    private fun loadModel(modelPath: String): Module {
        return Module.load(modelPath)
    }

    override fun initialize() {
        val isVocabLoaded = vocabUtils.loadFiltersAndVocab(vocabPath)
        module = loadModel(File(context.filesDir, modelPath).absolutePath)

        isInitialized = isVocabLoaded
    }

    override fun release() {
        module.destroy()
    }

    private fun decode(encoderOutputs: EValue, language: String): FloatArray {
        val maxSeqLen = module.execute("get_max_seq_len", EValue.optionalNone())[0].toInt().toInt()
        val noTimeStampTokenId = module.execute("no_timestamps_token_id")[0].toInt()
        val forcedTokens = longArrayOf(
            VocabUtils.SOT.toLong(),
            VocabUtils.Companion.LanguageCode.findByName(language).code,
            VocabUtils.TRANSCRIBE.toLong(),
            50364L // 시작 토큰값 [0.00]
        )

        Log.d("test", "maxSegLen: $maxSeqLen")

        val generatedIds = mutableListOf<Float>()

        for (i in 0 until maxSeqLen) {
            val decoderInputIds = if (i < forcedTokens.size) {
                Tensor.fromBlob(
                    longArrayOf(forcedTokens[i]),
                    longArrayOf(1, 1)
                )
            } else {
                Tensor.fromBlob(
                    longArrayOf(generatedIds.last().toLong()),
                    longArrayOf(1, 1)
                )
            }

            val cachePosition = Tensor.fromBlob(
                longArrayOf(i.toLong()),
                longArrayOf(1)
            )

            val logits = module.execute(
                "text_decoder",
                EValue.from(decoderInputIds),
                encoderOutputs,
                EValue.from(cachePosition)
            )[0].toTensor()

            val nextToken = if (i < forcedTokens.size) {
                forcedTokens[i].toFloat()
            } else {
                argmax(logits, noTimeStampTokenId)
            }

            generatedIds.add(nextToken)

            if (nextToken == VocabUtils.Companion.EOT.toFloat()) break
        }

        return generatedIds.toFloatArray()
    }

    private fun argmax(
        logits: Tensor,
        suppressTokenId: Long = VocabUtils.Companion.NOT.toLong()
    ): Float {
        val shape = logits.shape()
        val vocabSize = shape[2].toInt()
        val data = logits.dataAsFloatArray
        val offset = ((shape[0] * shape[1] - 1) * vocabSize).toInt()

        data[offset + suppressTokenId.toInt()] = Float.NEGATIVE_INFINITY

        var maxIdx = 0
        var maxVal = Float.NEGATIVE_INFINITY

        for (i in 0 until vocabSize) {
            val value = data[offset + i]
            if (value > maxVal) {
                maxVal = value
                maxIdx = i
            }
        }

        return maxIdx.toFloat()
    }

    override suspend fun transcribeByModel(audioData: FloatArray, language: String) {
        module = loadModel(File(context.filesDir, modelPath).absolutePath)

        val melSpectrogram = vocabUtils.calMelSpectrogram(audioData)
        val tensor1 = Tensor.fromBlob(melSpectrogram, longArrayOf(1, 80, 3000))
        val eValue1 = EValue.from(tensor1)

        val result = runCatching {
            val encoded = module.execute("encoder", eValue1)[0]

            // module.execute("text_decoder", encoded)[0].toTensor().dataAsFloatArray

            decode(encoderOutputs = encoded, language = language)
        }
        Log.d("test", "d: ${module.readLogBuffer().joinToString(", ")}")

        result.onSuccess {
            Log.d(
                "test", "추론 끝: ${
                    it.run {
                        StringBuilder().apply {
                            repeat(this@run.size) { idx ->
                                append("${this@run[idx]} ")
                            }
                        }.toString()
                    }
                }"
            )
        }

        module.destroy()

        postProcessing(
            inferenceResult = result.getOrThrow(),
            inferenceInfo = transcribeResult,
        )
    }

    /**
     * 분할된 오디오 데이터를 추론하여 결과를 자막 형식에 맞게 모아놓는 함수
     *
     * 1. 30초 단위로 분할되어 채널에 들어오는 전처리된 오디오 데이터를 추론 수행
     * 2. 추론 결과로 반환된 30초 분량의 자막 텍스트를 반환
     *
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun postProcessing(
        inferenceResult: FloatArray,
        inferenceInfo: InferenceInfo,
    ): InferenceInfo {
        if (inferenceResult.isNotEmpty()) {
            val srtResult = convertToSRT(
                inferenceResult,
                inferenceInfo,
            )

            Log.d("test", "세그먼트 내의 srtResult: $srtResult")

            parseLastEndTimeFromSrt(srtText = srtResult, inferenceInfo = inferenceInfo)

            Log.d(
                "test",
                "lastTimestamp: ${inferenceInfo.lastSeconds}, lastIndex: ${inferenceInfo.index}"
            )

            inferenceInfo.transcription.append(srtResult)
        }

        return inferenceInfo
    }

    private fun convertToSRT(
        output: FloatArray,
        inferenceInfo: InferenceInfo,
    ): String {
        val segments = mutableListOf<Triple<Double, Double, ByteArray>>()
        val currTextTokens = mutableListOf<Byte>()
        var currStart = 0.0
        var hasStart = false

        for (i in 0 until output.size) {
            val token = output[i].toInt()

            when (token) {
                in 0 until VocabUtils.EOT -> currTextTokens.addAll(
                    vocabUtils.getWordByToken(token)!!.toList()
                )

                VocabUtils.EOT -> break
                in VocabUtils.BEGIN..VocabUtils.END -> {
                    val ts = TS_STEP * (token - VocabUtils.BEGIN) + inferenceInfo.lastSeconds
                    if (!hasStart) {
                        // 시작 타임스탬프
                        currStart = ts
                        hasStart = true
                    } else {
                        // 끝 타임스탬프
                        if (currTextTokens.isNotEmpty()) {
                            segments.add(Triple(currStart, ts, currTextTokens.toByteArray()))
                        }
                        currTextTokens.clear()
                        currStart = ts
                    }
                }
            }
        }

        // 마지막 텍스트 처리
        if (hasStart && currTextTokens.isNotEmpty()) {
            val endTs = currStart + 30
            segments.add(Triple(currStart, endTs, currTextTokens.toByteArray()))
        }

        // SRT 변환
        val srt = StringBuilder()
        for ((idx, segment) in segments.withIndex()) {
            val (start, end, text) = segment
            srt.append("${idx + 1 + inferenceInfo.index}\n")
            srt.append("${formatTimestamp(start)} --> ${formatTimestamp(end)}\n")
            srt.append("${String(text, UTF_8).trim()}\n\n")
        }

        return srt.toString()
    }

    private fun formatTimestamp(t: Double): String {
        val h = (t / 3600).toInt()
        val m = ((t % 3600) / 60).toInt()
        val s = (t % 60).toInt()
        val ms = ((t - t.toInt()) * 1000).toInt()
        return String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d,%03d",
            h,
            m,
            s,
            ms
        )
    }

    /**
     * SRT 텍스트에서 마지막 종료 시간 파싱
     */
    private fun parseLastEndTimeFromSrt(srtText: String, inferenceInfo: InferenceInfo) {
        val pattern = """-->\s*(\d{2}):(\d{2}):(\d{2}),(\d{3})""".toRegex()
        val matches = pattern.findAll(srtText)

        if (matches.none()) {
            inferenceInfo.apply {
                index = inferenceInfo.index
                lastSeconds = inferenceInfo.lastSeconds + 30
            }
            return
        }

        val lastMatch = matches.last()
        val hours = lastMatch.groupValues[1].toInt()
        val minutes = lastMatch.groupValues[2].toInt()
        val seconds = lastMatch.groupValues[3].toInt()
        val milliseconds = lastMatch.groupValues[4].toInt()

        val lastEndPos = lastMatch.range.first

        val textBeforeLastEnd = srtText.take(lastEndPos)
        val lines = textBeforeLastEnd.lines().asReversed()

        inferenceInfo.apply {
            index = lines.firstOrNull { it.trim().matches(Regex("^\\d+$")) }?.trim()?.toInt() ?: 0
            lastSeconds = hours * 3600 + minutes * 60 + seconds + milliseconds / 1000.0f
        }
    }

    data class InferenceInfo(
        var index: Int,
        var lastSeconds: Float,
        override val transcription: StringBuilder,
    ) : TranscribeResult()
}