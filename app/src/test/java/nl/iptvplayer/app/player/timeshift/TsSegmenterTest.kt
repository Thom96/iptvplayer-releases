package nl.iptvplayer.app.player.timeshift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TsSegmenterTest {
    private fun cuts(stream: ByteArray, segmenter: TsSegmenter): List<Pair<Int, Long>> {
        val result = ArrayList<Pair<Int, Long>>()
        var off = 0
        while (off + 188 <= stream.size) {
            val closed = segmenter.accept(stream, off)
            if (closed >= 0) result += off to closed
            off += 188
        }
        return result
    }

    @Test
    fun cutsAtKeyFramesWithStreamDurations() {
        val stream = TsTestStream.build(seconds = 20, gopFrames = 50) // key frame every 2 s
        val result = cuts(stream, TsSegmenter(clockMs = { 0L }))
        assertEquals(9, result.size)
        result.forEach { (off, duration) ->
            assertEquals(2_000L, duration)
            // Cut right before a key frame packet with the random access flag.
            assertEquals(0x40, stream[off + 5].toInt() and 0x40)
        }
    }

    @Test
    fun cutsAtMaximumWithoutKeyFrames() {
        val stream = TsTestStream.build(seconds = 30, gopFrames = 10_000) // only the first frame is a key frame
        val result = cuts(stream, TsSegmenter(clockMs = { 0L }))
        assertTrue(result.isNotEmpty())
        result.forEach { (_, duration) -> assertEquals(8_000L, duration) }
    }

    @Test
    fun headerHoldsPatAndPmt() {
        val segmenter = TsSegmenter(clockMs = { 0L })
        assertEquals(0, segmenter.header().size)
        cuts(TsTestStream.build(seconds = 1), segmenter)
        val header = segmenter.header()
        assertEquals(376, header.size)
        assertEquals(0, header[2].toInt()) // PAT pid
        assertEquals(0x10, header[188 + 2].toInt()) // PMT pid
    }

    @Test
    fun clockWrapStillGivesPositiveDurations() {
        val nearWrap = (1L shl 33) / 90 - 3_000 // 3 s before the 33-bit clock wraps
        val result = cuts(TsTestStream.build(seconds = 10, firstPcrMs = nearWrap), TsSegmenter(clockMs = { 0L }))
        result.forEach { (_, duration) -> assertEquals(2_000L, duration) }
    }
}
