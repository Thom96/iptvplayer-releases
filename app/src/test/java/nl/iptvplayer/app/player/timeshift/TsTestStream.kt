package nl.iptvplayer.app.player.timeshift

import java.io.ByteArrayOutputStream

/** A made-up transport stream: 25 frames per second, a key frame every [gopFrames] frames. */
object TsTestStream {
    const val VIDEO_PID = 0x100

    fun build(seconds: Int, gopFrames: Int = 50, firstPcrMs: Long = 0): ByteArray {
        val out = ByteArrayOutputStream()
        for (frame in 0 until seconds * 25) {
            if (frame % 25 == 0) {
                out.write(pat())
                out.write(pmt())
            }
            val pcr = (firstPcrMs + frame * 40L) * 90
            out.write(video(pcr, keyFrame = frame % gopFrames == 0))
            out.write(payloadOnly())
        }
        return out.toByteArray()
    }

    private fun packet(pid: Int, pusi: Boolean, afc: Int): ByteArray = ByteArray(188) { 0xFF.toByte() }.also {
        it[0] = 0x47
        it[1] = ((if (pusi) 0x40 else 0) or (pid shr 8 and 0x1F)).toByte()
        it[2] = (pid and 0xFF).toByte()
        it[3] = ((afc shl 4) or 0).toByte()
    }

    fun pat(): ByteArray = packet(0, true, 1).also {
        val section = intArrayOf(0x00, 0xB0, 0x0D, 0x00, 0x01, 0xC1, 0x00, 0x00, 0x00, 0x01, 0xE0, 0x10, 0, 0, 0, 0)
        it[4] = 0 // pointer field
        section.forEachIndexed { i, b -> it[5 + i] = b.toByte() }
    }

    fun pmt(): ByteArray = packet(0x10, true, 1).also {
        // PMT on pid 0x10; PCR pid and one H.264 stream on VIDEO_PID.
        val section = intArrayOf(
            0x02, 0xB0, 0x12, 0x00, 0x01, 0xC1, 0x00, 0x00,
            0xE0 or (VIDEO_PID shr 8), VIDEO_PID and 0xFF, 0xF0, 0x00,
            0x1B, 0xE0 or (VIDEO_PID shr 8), VIDEO_PID and 0xFF, 0xF0, 0x00,
            0, 0, 0, 0,
        )
        it[4] = 0
        section.forEachIndexed { i, b -> it[5 + i] = b.toByte() }
    }

    private fun video(pcr: Long, keyFrame: Boolean): ByteArray = packet(VIDEO_PID, true, 3).also {
        it[4] = 7 // adaptation field length
        it[5] = ((if (keyFrame) 0x40 else 0) or 0x10).toByte()
        it[6] = (pcr shr 25).toByte()
        it[7] = (pcr shr 17).toByte()
        it[8] = (pcr shr 9).toByte()
        it[9] = (pcr shr 1).toByte()
        it[10] = ((pcr and 1) shl 7).toByte()
        it[11] = 0
    }

    private fun payloadOnly(): ByteArray = packet(VIDEO_PID, false, 1)
}
