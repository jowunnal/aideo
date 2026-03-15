package jinproject.aideo.core.media.audio

/**
 * 임의 크기의 오디오 샘플을 고정 크기(windowSize) 청크로 분할하여 콜백으로 전달.
 *
 * 내부 버퍼 하나만 고정 할당하며, 잔여 샘플은 다음 [feed] 호출 시 이어서 처리.
 *
 * 주의: [onChunkReady]에 내부 버퍼가 직접 전달될 수 있으므로, 콜백에서 참조를 보관하지 말 것.
 *
 * @param windowSize 청크 크기 (기본값 512)
 * @param onChunkReady 청크가 준비될 때마다 호출되는 콜백
 */
class ChunkedAudioProcessor(
    private val windowSize: Int = 512,
    private val onChunkReady: suspend (FloatArray) -> Unit,
) {
    private val buffer = FloatArray(windowSize)
    private var position = 0

    suspend fun feed(samples: FloatArray) {
        var offset = 0

        if (position > 0) {
            val needed = windowSize - position
            val toCopy = minOf(needed, samples.size)
            samples.copyInto(buffer, destinationOffset = position, endIndex = toCopy)
            position += toCopy
            offset = toCopy

            if (position == windowSize) {
                onChunkReady(buffer)
                position = 0
            }
        }

        while (offset + windowSize <= samples.size) {
            onChunkReady(samples.copyOfRange(offset, offset + windowSize))
            offset += windowSize
        }

        if (offset < samples.size) {
            samples.copyInto(buffer, destinationOffset = 0, startIndex = offset)
            position = samples.size - offset
        }
    }

    fun flush(): FloatArray = buffer.copyOf(position).also { position = 0 }
}
