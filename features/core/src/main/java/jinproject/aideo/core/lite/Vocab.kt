package jinproject.aideo.core.lite

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.support.common.FileUtil
import java.lang.Float
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.FloatArray
import kotlin.Int
import kotlin.String
import kotlin.Unit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.round
import kotlin.math.sin
import kotlin.run

class VocabUtils {

    val filters: WhisperFilter = WhisperFilter()
    var tokenToWord: MutableMap<Int, ByteArray> = HashMap<Int, ByteArray>()

    fun loadFiltersAndVocab(context: Context, vocabPath: String): Boolean {
        val vocabBuf = ByteBuffer.wrap(Files.readAllBytes(Paths.get(vocabPath)))
        vocabBuf.order(ByteOrder.nativeOrder())

        val magic = vocabBuf.getInt()
        if (magic == 0x5553454e) {
            Log.d("test", "Magic number: $magic")
        } else {
            Log.d("test", "Invalid vocab file (bad magic: $magic), $vocabPath")
            return false
        }

        filters.nMel = vocabBuf.getInt()
        filters.nFft = vocabBuf.getInt()

        val filterData = ByteArray(filters.nMel * filters.nFft * Float.BYTES)
        vocabBuf.get(filterData, 0, filterData.size)
        val filterBuf = ByteBuffer.wrap(filterData)
        filterBuf.order(ByteOrder.nativeOrder())

        filters.data = FloatArray(filters.nMel * filters.nFft)
        run {
            var i = 0
            while (filterBuf.hasRemaining()) {
                filters.data[i] = filterBuf.getFloat()
                i++
            }
        }

        val nVocab = vocabBuf.getInt()
        for (i in 0..<nVocab) {
            val len = vocabBuf.getInt()
            val wordBytes = ByteArray(len)
            vocabBuf.get(wordBytes, 0, wordBytes.size)
            tokenToWord.put(i, wordBytes)
        }

        for (i in nVocab..<Vocab.MULTILINGUAL.token) {
            val word = if (i > Vocab.BEGIN.token) {
                "[_TT_" + (i - Vocab.BEGIN.token) + "]"
            } else if (i == Vocab.EOT.token) {
                "[_EOT_]"
            } else if (i == Vocab.SOT.token) {
                "[_SOT_]"
            } else if (i == Vocab.PREV.token) {
                "[_PREV_]"
            } else if (i == Vocab.NOT.token) {
                "[_NOT_]"
            } else if (i == Vocab.BEGIN.token) {
                "[_BEG_]"
            } else if (i >= Vocab.BEGIN.token && i <= 51863) {
                val ts = 0.02 * (i - Vocab.BEGIN.token)
                "${round(ts * 100.0) / 100.0}"
            } else {
                "[_extra_token_$i]"
            }

            tokenToWord.put(i, word.toByteArray(StandardCharsets.UTF_8))
        }

        return true
    }

    enum class Vocab(val token: Int) {
        EOT(token = 50257),
        BEGIN(token = 50364),
        SOT(token = 50258),
        PREV(token = 50361),
        SOLM(token = 50362),
        NOT(token = 50363),
        TRANSCRIBE(token = 50360),
        TRANSLATE(token = 50359),
        MULTILINGUAL(token = 51865),
    }

    class WhisperFilter(
        var nMel: Int = 0,
        var nFft: Int = 0,
        var data: FloatArray = FloatArray(0),
    )
}