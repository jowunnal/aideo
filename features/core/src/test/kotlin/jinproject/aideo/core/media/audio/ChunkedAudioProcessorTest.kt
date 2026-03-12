package jinproject.aideo.core.media.audio

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.shouldBeExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChunkedAudioProcessorTest {

    private val chunks = mutableListOf<FloatArray>()
    private lateinit var processor: ChunkedAudioProcessor

    @BeforeEach
    fun setUp() {
        chunks.clear()
        processor = ChunkedAudioProcessor(windowSize = 512) { chunks.add(it.copyOf()) }
    }

    // -------------------------------------------------------------------------
    // 1. Exact window size triggers exactly one callback, no remainder
    // -------------------------------------------------------------------------
    @Test
    fun `정확히 512 샘플 전달 시 콜백 1회 호출되고 flush는 빈 배열 반환`() =
        runTest {
            processor.feed(FloatArray(512))

            chunks shouldHaveSize 1
            chunks[0].size shouldBe 512
            processor.flush().size shouldBe 0
        }

    // -------------------------------------------------------------------------
    // 2. Fewer than windowSize samples produce no callback; samples held in buffer
    // -------------------------------------------------------------------------
    @Test
    fun `512 미만 샘플은 콜백 없이 잔여로 보관`() =
        runTest {
            processor.feed(FloatArray(300))

            chunks shouldHaveSize 0
            processor.flush().size shouldBe 300
        }

    // -------------------------------------------------------------------------
    // 3. Exactly two windows triggers two callbacks, no remainder
    // -------------------------------------------------------------------------
    @Test
    fun `1024 샘플 전달 시 콜백 2회 호출되며 각 512개`() =
        runTest {
            processor.feed(FloatArray(1024))

            chunks shouldHaveSize 2
            chunks[0].size shouldBe 512
            chunks[1].size shouldBe 512
            processor.flush().size shouldBe 0
        }

    // -------------------------------------------------------------------------
    // 4. Remainder accumulates across multiple feed calls
    // -------------------------------------------------------------------------
    @Test
    fun `300 샘플 2회 전달 시 콜백 1회 호출되고 잔여 88개`() =
        runTest {
            processor.feed(FloatArray(300))
            processor.feed(FloatArray(300))

            // 300 + 300 = 600 → one full window of 512, remainder = 88
            chunks shouldHaveSize 1
            chunks[0].size shouldBe 512
            processor.flush().size shouldBe 88
        }

    // -------------------------------------------------------------------------
    // 5. Flush after an exact window returns an empty array
    // -------------------------------------------------------------------------
    @Test
    fun `잔여 없을 때 flush는 빈 배열 반환`() =
        runTest {
            processor.feed(FloatArray(512))

            val remainder = processor.flush()
            remainder.size shouldBe 0
        }

    // -------------------------------------------------------------------------
    // 6. Large input yields one full chunk and the leftover in the buffer
    // -------------------------------------------------------------------------
    @Test
    fun `1000 샘플 전달 시 콜백 1회 호출되고 잔여 488개`() =
        runTest {
            processor.feed(FloatArray(1000))

            // 1000 → one window of 512, remainder = 488
            chunks shouldHaveSize 1
            chunks[0].size shouldBe 512
            processor.flush().size shouldBe 488
        }

    // -------------------------------------------------------------------------
    // 7. Data integrity: values in callback chunk and flushed remainder are correct
    // -------------------------------------------------------------------------
    @Test
    fun `600개 순차 값 전달 시 청크는 0~511이고 잔여는 512~599`() =
        runTest {
            val input = FloatArray(600) { it.toFloat() }
            processor.feed(input)

            chunks shouldHaveSize 1
            val chunk = chunks[0]
            chunk.size shouldBe 512
            for (i in 0 until 512) {
                chunk[i] shouldBeExactly i.toFloat()
            }

            val remainder = processor.flush()
            remainder.size shouldBe 88
            for (i in 0 until 88) {
                remainder[i] shouldBeExactly (512 + i).toFloat()
            }
        }

    // -------------------------------------------------------------------------
    // 8. Custom windowSize is respected
    // -------------------------------------------------------------------------
    @Test
    fun `커스텀 windowSize 100으로 250 샘플 전달 시 콜백 2회 호출되고 잔여 50개`() =
        runTest {
            val customChunks = mutableListOf<FloatArray>()
            val customProcessor =
                ChunkedAudioProcessor(windowSize = 100) { customChunks.add(it.copyOf()) }

            customProcessor.feed(FloatArray(250))

            customChunks shouldHaveSize 2
            customChunks[0].size shouldBe 100
            customChunks[1].size shouldBe 100
            customProcessor.flush().size shouldBe 50
        }

    // -------------------------------------------------------------------------
    // 9. 여러 feed에 걸쳐 잔여가 합쳐질 때 값 순서가 보존되는지 검증
    // -------------------------------------------------------------------------
    @Test
    fun `잔여 누적 시 값 순서가 보존된다`() = runTest {
        val input1 = FloatArray(300) { it.toFloat() }           // 0..299
        val input2 = FloatArray(300) { (it + 300).toFloat() }   // 300..599

        processor.feed(input1)
        processor.feed(input2)

        // 300 + 300 = 600 → 청크 512개 + 잔여 88개
        chunks shouldHaveSize 1
        for (i in 0 until 512) {
            chunks[0][i] shouldBeExactly i.toFloat()
        }

        val remainder = processor.flush()
        for (i in 0 until 88) {
            remainder[i] shouldBeExactly (512 + i).toFloat()
        }
    }
}
