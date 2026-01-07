package jinproject.aideo.core.inference.whisper

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Paths
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

class VocabUtils @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private lateinit var filters: FloatArray
    private val tokenToWord: MutableMap<Int, ByteArray> = HashMap<Int, ByteArray>()

    fun getWordByToken(token: Int): ByteArray? {
        return tokenToWord[token]
    }

    fun loadFiltersAndVocab(vocabPath: String): Boolean {
        val vocabBuf = ByteBuffer.wrap(context.assets.open(vocabPath).readAllBytes()).apply {
            order(ByteOrder.LITTLE_ENDIAN)
        }

        val magic = vocabBuf.getInt()
        if (magic == 0x57535052) {
            Log.d("test", "Magic number: $magic")
        } else {
            Log.d("test", "Invalid vocab file (bad magic: $magic), $vocabPath")
            return false
        }

        val nMel = vocabBuf.getInt()
        val nFft = vocabBuf.getInt()
        val length = nMel * nFft * Float.SIZE_BYTES

        val filterData = ByteArray(length)
        vocabBuf.get(filterData, 0, filterData.size)

        val filterBuf = ByteBuffer.wrap(filterData).apply {
            order(ByteOrder.LITTLE_ENDIAN)
        }

        filters = FloatArray(nMel * nFft).apply {
            var i = 0
            while (filterBuf.hasRemaining()) {
                this@apply[i++] = filterBuf.getFloat()
            }
        }

        val nVocab = vocabBuf.getInt()
        for (i in 0 until nVocab) { //TODO 너무 많은 byteArray 가 생성되지 않을까?
            val len = vocabBuf.getInt()
            val wordBytes = ByteArray(len)
            vocabBuf.get(wordBytes, 0, wordBytes.size)
            tokenToWord[i] = wordBytes
        }

        return true
    }

    suspend fun calMelSpectrogram(audioData: FloatArray): FloatArray =
        withContext(Dispatchers.Default) {
            val fftSize = WHISPER_N_FFT
            val fftStep = WHISPER_HOP_LENGTH
            val nMel = WHISPER_N_MEL

            val nLen = audioData.size / fftStep
            val melData = FloatArray(nLen * nMel)

            val meaningfulFrames = audioData.size / fftStep

            val hann = FloatArray(fftSize)

            for (i in 0 until hann.size) {
                hann[i] = (0.5 * (1.0 - cos(2.0 * Math.PI * i / fftSize))).toFloat()
            }

            val nFft = 1 + fftSize / 2

            val availableProcessors = Runtime.getRuntime().availableProcessors()
            val jobs = mutableListOf<Job>()

            for (threadCount in 0 until availableProcessors) {
                jobs.add(launch {
                    val fftIn = FloatArray(fftSize)

                    val fftOut = FloatArray(fftSize * 2)

                    for (i in threadCount until meaningfulFrames step availableProcessors) {
                        // Limit to meaningful frames
                        val offset = i * fftStep


                        // apply Hanning window
                        for (j in 0 until fftSize) {
                            if (offset + j < audioData.size) { // Limit to meaningful samples
                                fftIn[j] = hann[j] * audioData[offset + j]
                            } else {
                                fftIn[j] = 0.0f
                            }
                        }


                        // FFT -> mag^2
                        fft(fftIn, fftOut)
                        for (j in 0 until fftSize) {
                            fftOut[j] =
                                fftOut[2 * j] * fftOut[2 * j] + fftOut[2 * j + 1] * fftOut[2 * j + 1]
                        }

                        for (j in 1 until fftSize / 2) {
                            fftOut[j] += fftOut[fftSize - j]
                        }


                        // mel spectrogram
                        for (j in 0 until nMel) {
                            var sum = 0.0
                            for (k in 0 until nFft) {
                                sum += (fftOut[k] * filters[j * nFft + k]).toDouble()
                            }

                            if (sum < 1e-10) {
                                sum = 1e-10
                            }

                            sum = log10(sum)
                            melData[j * nLen + i] = sum.toFloat()
                        }
                    }

                    // Pad the remaining frames with a default value (e.g., -8.0)
                    for (i in threadCount + meaningfulFrames until nLen step availableProcessors) {
                        for (j in 0 until nMel) {
                            melData[j * nLen + i] = -8.0f // Default padding value
                        }
                    }
                })
            }

            jobs.onEach { it.join() }

            // clamping and normalization
            var mmax = -1e20
            for (i in 0 until nMel * nLen) {
                if (melData[i] > mmax) {
                    mmax = melData[i].toDouble()
                }
            }

            mmax -= 8.0
            for (i in 0 until nMel * nLen) {
                if (melData[i] < mmax) {
                    melData[i] = mmax.toFloat()
                }
                melData[i] = ((melData[i] + 4.0) / 4.0).toFloat()
            }

            return@withContext melData
        }

    private fun dft(input: FloatArray, output: FloatArray) {
        val inSize = input.size
        for (k in 0 until inSize) {
            var re = 0.0f
            var im = 0.0f
            for (n in 0 until inSize) {
                val angle = (2 * Math.PI * k * n / inSize).toFloat()
                re += (input[n] * cos(angle.toDouble())).toFloat()
                im -= (input[n] * sin(angle.toDouble())).toFloat()
            }
            output[k * 2 + 0] = re
            output[k * 2 + 1] = im
        }
    }

    private fun fft(input: FloatArray, output: FloatArray) {
        val inSize = input.size
        if (inSize == 1) {
            output[0] = input[0]
            output[1] = 0.0f
            return
        }

        if (inSize % 2 == 1) {
            dft(input, output)
            return
        }

        val even = FloatArray(inSize / 2)
        val odd = FloatArray(inSize / 2)

        var indxEven = 0
        var indxOdd = 0
        for (i in 0 until inSize) {
            if (i % 2 == 0) {
                even[indxEven] = input[i]
                indxEven++
            } else {
                odd[indxOdd] = input[i]
                indxOdd++
            }
        }

        val evenFft = FloatArray(inSize)
        val oddFft = FloatArray(inSize)

        fft(even, evenFft)
        fft(odd, oddFft)
        for (k in 0 until inSize / 2) {
            val theta = (2 * Math.PI * k / inSize).toFloat()
            val re = cos(theta.toDouble()).toFloat()
            val im = -sin(theta.toDouble()).toFloat()
            val reOdd = oddFft[2 * k + 0]
            val imOdd = oddFft[2 * k + 1]
            output[2 * k + 0] = evenFft[2 * k + 0] + re * reOdd - im * imOdd
            output[2 * k + 1] = evenFft[2 * k + 1] + re * imOdd + im * reOdd
            output[2 * (k + inSize / 2) + 0] = evenFft[2 * k + 0] - re * reOdd + im * imOdd
            output[2 * (k + inSize / 2) + 1] = evenFft[2 * k + 1] - re * imOdd - im * reOdd
        }
    }

    companion object {
        const val WHISPER_N_FFT = 400
        const val WHISPER_N_MEL = 80
        const val WHISPER_HOP_LENGTH = 160
        const val EOT = 50257
        const val BEGIN = 50364
        const val SOT = 50258
        const val PREV = 50361
        const val SOLM = 50362
        const val NOT = 50363
        const val TRANSCRIBE = 50359
        const val TRANSLATE = 50358
        const val MULTILINGUAL = 51865
        const val END = 51864

        enum class LanguageCode(val code: Long) {
            ko(50264L),
            en(50259L),
            ja(50266L),
            zh(50260L);

            companion object {
                fun findByName(name: String): LanguageCode = LanguageCode.entries.find { it.name == name } ?: ko
            }
        }
    }
}