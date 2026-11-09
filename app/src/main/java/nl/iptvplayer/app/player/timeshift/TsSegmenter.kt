package nl.iptvplayer.app.player.timeshift

/**
 * Splits an MPEG transport stream into short, independently playable pieces.
 *
 * It follows the tables of the stream (PAT and PMT) to find the video and clock (PCR) streams, and cuts
 * before a video key frame (random access indicator) once a piece is long enough. Every piece then starts
 * with a key frame, and [header] (the latest PAT and PMT) is written in front of it, so playback can start
 * at any piece. Durations come from the stream's own clock, so a burst of data at the start of a connection
 * doesn't count as real time.
 */
class TsSegmenter(
    private val minDurationMs: Long = 2_000,
    private val maxDurationMs: Long = 8_000,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    private var pmtPid = -1
    private var pcrPid = -1
    private var videoPid = -1
    private var pat: ByteArray? = null
    private var pmt: ByteArray? = null

    private var segmentStartPcr = -1L
    private var lastPcr = -1L
    private var segmentStartClock = clockMs()
    private var segmentHasData = false

    /** PAT + PMT to write at the start of a new piece; empty until both were seen. */
    fun header(): ByteArray {
        val a = pat ?: return ByteArray(0)
        val b = pmt ?: return ByteArray(0)
        return a + b
    }

    /** Duration of the current piece so far. */
    fun currentDurationMs(): Long {
        if (segmentStartPcr >= 0 && lastPcr >= 0) {
            val ms = pcrDiff(lastPcr, segmentStartPcr) / 90
            // A jump in the clock (new programme, server hiccup): fall back to the wall clock.
            if (ms in 0..MAX_PLAUSIBLE_MS) return ms
        }
        return (clockMs() - segmentStartClock).coerceAtLeast(0)
    }

    /**
     * Feeds one 188-byte packet at [off]. Returns the duration of the piece that ends before this packet if a
     * new piece must start here, or -1 to keep writing to the current piece.
     */
    fun accept(buf: ByteArray, off: Int): Long {
        val pid = ((buf[off + 1].toInt() and 0x1F) shl 8) or (buf[off + 2].toInt() and 0xFF)
        val pusi = buf[off + 1].toInt() and 0x40 != 0
        val afc = (buf[off + 3].toInt() shr 4) and 0x3
        var payload = off + 4
        var randomAccess = false
        var pcr = -1L
        if (afc and 0x2 != 0) {
            val len = buf[off + 4].toInt() and 0xFF
            if (len > 0 && len <= 183) {
                val flags = buf[off + 5].toInt() and 0xFF
                randomAccess = flags and 0x40 != 0
                if (flags and 0x10 != 0 && len >= 7) {
                    pcr = ((buf[off + 6].toLong() and 0xFF) shl 25) or
                        ((buf[off + 7].toLong() and 0xFF) shl 17) or
                        ((buf[off + 8].toLong() and 0xFF) shl 9) or
                        ((buf[off + 9].toLong() and 0xFF) shl 1) or
                        ((buf[off + 10].toLong() and 0xFF) shr 7)
                }
            }
            payload = off + 5 + len
        }

        if (pid == 0 && pusi) parsePat(buf, off, payload)
        else if (pid == pmtPid && pusi) parsePmt(buf, off, payload)

        if (pcr >= 0 && (pcrPid < 0 || pid == pcrPid)) {
            lastPcr = pcr
            if (segmentStartPcr < 0) segmentStartPcr = pcr
        }

        val elapsed = currentDurationMs()
        val isVideo = videoPid < 0 || pid == videoPid
        val cut = segmentHasData && isVideo && pusi && (
            (randomAccess && elapsed >= minDurationMs) || elapsed >= maxDurationMs
            )
        if (cut) {
            segmentStartPcr = lastPcr
            segmentStartClock = clockMs()
            segmentHasData = true
            return elapsed
        }
        segmentHasData = true
        return -1
    }

    private fun parsePat(buf: ByteArray, off: Int, payload: Int) {
        val table = sectionStart(buf, off, payload) ?: return
        if (buf[table].toInt() and 0xFF != 0x00) return
        val sectionLength = ((buf[table + 1].toInt() and 0x0F) shl 8) or (buf[table + 2].toInt() and 0xFF)
        val end = minOf(table + 3 + sectionLength - 4, off + PACKET)
        var i = table + 8
        while (i + 4 <= end) {
            val program = ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            val pid = ((buf[i + 2].toInt() and 0x1F) shl 8) or (buf[i + 3].toInt() and 0xFF)
            if (program != 0) {
                pmtPid = pid
                break
            }
            i += 4
        }
        pat = buf.copyOfRange(off, off + PACKET)
    }

    private fun parsePmt(buf: ByteArray, off: Int, payload: Int) {
        val table = sectionStart(buf, off, payload) ?: return
        if (buf[table].toInt() and 0xFF != 0x02) return
        val sectionLength = ((buf[table + 1].toInt() and 0x0F) shl 8) or (buf[table + 2].toInt() and 0xFF)
        val end = minOf(table + 3 + sectionLength - 4, off + PACKET)
        if (table + 12 > end) return
        pcrPid = ((buf[table + 8].toInt() and 0x1F) shl 8) or (buf[table + 9].toInt() and 0xFF)
        val infoLength = ((buf[table + 10].toInt() and 0x0F) shl 8) or (buf[table + 11].toInt() and 0xFF)
        var i = table + 12 + infoLength
        while (i + 5 <= end) {
            val type = buf[i].toInt() and 0xFF
            val pid = ((buf[i + 1].toInt() and 0x1F) shl 8) or (buf[i + 2].toInt() and 0xFF)
            val esLength = ((buf[i + 3].toInt() and 0x0F) shl 8) or (buf[i + 4].toInt() and 0xFF)
            if (type in VIDEO_TYPES) {
                videoPid = pid
                break
            }
            i += 5 + esLength
        }
        pmt = buf.copyOfRange(off, off + PACKET)
    }

    /** Start of the table in a packet that starts a section (after the pointer field). */
    private fun sectionStart(buf: ByteArray, off: Int, payload: Int): Int? {
        if (payload >= off + PACKET) return null
        val table = payload + 1 + (buf[payload].toInt() and 0xFF)
        return table.takeIf { it + 3 < off + PACKET }
    }

    private fun pcrDiff(a: Long, b: Long): Long = (a - b + PCR_WRAP) % PCR_WRAP

    companion object {
        const val PACKET = 188
        const val SYNC: Byte = 0x47
        private const val PCR_WRAP = 1L shl 33
        private const val MAX_PLAUSIBLE_MS = 60_000L
        /** MPEG-1/2, MPEG-4, H.264, HEVC and AVS video. */
        private val VIDEO_TYPES = setOf(0x01, 0x02, 0x10, 0x1B, 0x24, 0x42)
    }
}
