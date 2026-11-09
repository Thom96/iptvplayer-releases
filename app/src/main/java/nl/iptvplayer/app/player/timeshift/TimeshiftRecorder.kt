package nl.iptvplayer.app.player.timeshift

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Reads one live channel (MPEG-TS) from the network and keeps the last [maxDurationMs] of it on the device,
 * in pieces of a few seconds (see [TsSegmenter]). Playback reads from these files ([TimeshiftDataSource]),
 * so the picture can be paused and rewound while the recording continues.
 *
 * Pieces are numbered from 0; [Segment.startMs] is the time within the recording, counted from its start.
 */
class TimeshiftRecorder(
    private val http: OkHttpClient,
    val url: String,
    private val userAgent: String,
    private val dir: File,
    private val maxDurationMs: Long,
) {
    class Segment(val seq: Int, val file: File, val startMs: Long) {
        @Volatile var durationMs = 0L
        @Volatile var length = 0L
        @Volatile var complete = false
    }

    private val lock = Object()
    private val segments = ArrayList<Segment>() // guarded by lock
    @Volatile var error: IOException? = null
        private set
    @Volatile var stopped = false
        private set
    @Volatile private var call: okhttp3.Call? = null
    private var thread: Thread? = null

    val isHealthy: Boolean get() = !stopped && error == null

    fun start() {
        dir.deleteRecursively()
        dir.mkdirs()
        thread = Thread(::run, "timeshift").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        stopped = true
        call?.cancel()
        synchronized(lock) { lock.notifyAll() }
        // Files that are still open for reading stay readable until closed.
        Thread { dir.deleteRecursively() }.start()
    }

    // ---------- For playback ----------

    fun segment(seq: Int): Segment? = synchronized(lock) { segments.firstOrNull { it.seq == seq } }
    fun oldest(): Segment? = synchronized(lock) { segments.firstOrNull() }
    fun newest(): Segment? = synchronized(lock) { segments.lastOrNull() }

    /** The piece that contains [timeMs], or the nearest one. */
    fun segmentAt(timeMs: Long): Segment? = synchronized(lock) {
        segments.lastOrNull { it.startMs <= timeMs } ?: segments.firstOrNull()
    }

    /** Recorded time up to now (end of the newest piece). */
    fun liveEdgeMs(): Long = newest()?.let { it.startMs + it.durationMs } ?: 0L

    /** Waits until there is new data or the recording stops, at most [timeoutMs]. */
    fun await(timeoutMs: Long) {
        synchronized(lock) { if (!stopped && error == null) lock.wait(timeoutMs) }
    }

    // ---------- Recording ----------

    private fun run() {
        val segmenter = TsSegmenter()
        var out: FileOutputStream? = null
        var current: Segment? = null
        try {
            val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
            val c = http.newCall(request)
            call = c
            c.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val source = response.body?.byteStream() ?: throw IOException("Empty response")
                val buf = ByteArray(64 * 1024)
                var filled = 0
                current = newSegment(0, 0L)
                out = FileOutputStream(current!!.file)
                while (!stopped) {
                    val n = source.read(buf, filled, buf.size - filled)
                    if (n < 0) throw IOException("Stream ended")
                    filled += n
                    var pos = 0
                    var writeFrom = 0
                    while (filled - pos >= TsSegmenter.PACKET) {
                        if (buf[pos] != TsSegmenter.SYNC) {
                            // Lost sync: write what we have and skip to the next sync byte.
                            out!!.write(buf, writeFrom, pos - writeFrom)
                            pos++
                            while (pos < filled && buf[pos] != TsSegmenter.SYNC) pos++
                            writeFrom = pos
                            continue
                        }
                        val closed = segmenter.accept(buf, pos)
                        if (closed >= 0) {
                            out!!.write(buf, writeFrom, pos - writeFrom)
                            writeFrom = pos
                            out!!.close()
                            val done = current!!
                            done.length = done.file.length()
                            done.durationMs = closed
                            done.complete = true
                            current = newSegment(done.seq + 1, done.startMs + closed)
                            out = FileOutputStream(current!!.file)
                            out!!.write(segmenter.header())
                            trim()
                        }
                        pos += TsSegmenter.PACKET
                    }
                    out!!.write(buf, writeFrom, pos - writeFrom)
                    out!!.flush()
                    // Keep the rest (part of a packet) for the next read.
                    System.arraycopy(buf, pos, buf, 0, filled - pos)
                    filled -= pos
                    current!!.length = current!!.file.length()
                    current!!.durationMs = segmenter.currentDurationMs()
                    synchronized(lock) { lock.notifyAll() }
                }
            }
        } catch (e: IOException) {
            if (!stopped) {
                Log.w("IPTV", "Timeshift recording stopped: ${e.message}")
                error = e
            }
        } finally {
            runCatching { out?.close() }
            synchronized(lock) { lock.notifyAll() }
        }
    }

    private fun newSegment(seq: Int, startMs: Long): Segment {
        val s = Segment(seq, File(dir, "$seq.ts"), startMs)
        synchronized(lock) { segments += s }
        return s
    }

    /** Removes the oldest pieces beyond the chosen length, or when the device runs out of space. */
    private fun trim() {
        val removed = ArrayList<Segment>()
        synchronized(lock) {
            while (segments.size > MIN_SEGMENTS) {
                val first = segments.first()
                val last = segments.last()
                val duration = last.startMs - first.startMs
                val bytes = segments.sumOf { it.length }
                val tooLong = duration > maxDurationMs
                val tooBig = bytes > MAX_BYTES || dir.usableSpace < MIN_FREE_BYTES
                if (!tooLong && !tooBig) break
                removed += segments.removeAt(0)
            }
        }
        removed.forEach { it.file.delete() }
    }

    private companion object {
        const val MIN_SEGMENTS = 3
        /** At most this much on the device, whatever the chosen length (an hour of HD is about 3 GB). */
        const val MAX_BYTES = 4L * 1024 * 1024 * 1024
        /** Always leave this much free for the system and other apps. */
        const val MIN_FREE_BYTES = 500L * 1024 * 1024
    }
}
