package nl.iptvplayer.app.player.timeshift

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile

/**
 * Reads a recording of [LiveTimeshift] as one endless stream, starting at a piece and following the pieces
 * that are still being written. Other addresses go to [upstream].
 */
@UnstableApi
class TimeshiftDataSource private constructor(
    private val timeshift: LiveTimeshift,
) : BaseDataSource(false) {

    class Factory(private val timeshift: LiveTimeshift, private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = Routing(TimeshiftDataSource(timeshift), upstream.createDataSource())
    }

    private var recorder: TimeshiftRecorder? = null
    private var uri: Uri? = null
    private var seq = 0
    private var offset = 0L
    private var file: RandomAccessFile? = null
    @Volatile private var closed = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        closed = false
        uri = dataSpec.uri
        val (rec, origin) = timeshift.openForPlayback(dataSpec.uri, dataSpec.position == 0L)
        recorder = rec
        seq = origin
        offset = 0
        // Reopened halfway (after a read error): skip what was already read.
        var skip = dataSpec.position
        while (skip > 0) {
            val s = waitForSegment(rec)
            if (s.complete && s.length - offset <= skip) {
                skip -= s.length - offset
                seq++
                offset = 0
            } else if (s.length - offset > 0) {
                val n = minOf(skip, s.length - offset)
                offset += n
                skip -= n
            } else {
                rec.await(WAIT_MS)
            }
        }
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, off: Int, length: Int): Int {
        if (length == 0) return 0
        val rec = recorder ?: throw IOException("Not opened")
        val deadline = System.currentTimeMillis() + STALL_MS
        while (true) {
            if (closed) throw InterruptedIOException("Closed")
            if (Thread.interrupted()) throw InterruptedIOException()
            val s = waitForSegment(rec)
            val available = s.length - offset
            if (available > 0) {
                val f = file ?: RandomAccessFile(s.file, "r").also { file = it }
                f.seek(offset)
                val n = f.read(buffer, off, minOf(length.toLong(), available).toInt())
                if (n > 0) {
                    offset += n
                    bytesTransferred(n)
                    return n
                }
            }
            if (s.complete && offset >= s.length) {
                // On to the next piece.
                closeFile()
                seq++
                offset = 0
                continue
            }
            rec.error?.let { throw IOException("Recording stopped", it) }
            if (rec.stopped) throw IOException("Recording stopped")
            if (System.currentTimeMillis() > deadline) throw IOException("No data from the channel")
            rec.await(WAIT_MS)
        }
    }

    /** The current piece; if it was already removed (paused too long), the oldest piece that is left. */
    private fun waitForSegment(rec: TimeshiftRecorder): TimeshiftRecorder.Segment {
        val deadline = System.currentTimeMillis() + STALL_MS
        while (true) {
            if (closed) throw InterruptedIOException("Closed")
            rec.segment(seq)?.let { return it }
            val oldest = rec.oldest()
            if (oldest != null && oldest.seq > seq) {
                closeFile()
                seq = oldest.seq
                offset = 0
                return oldest
            }
            rec.error?.let { throw IOException("Recording stopped", it) }
            if (rec.stopped) throw IOException("Recording stopped")
            if (System.currentTimeMillis() > deadline) throw IOException("No data from the channel")
            rec.await(WAIT_MS)
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        closed = true
        closeFile()
        if (uri != null) {
            uri = null
            transferEnded()
        }
        recorder = null
    }

    private fun closeFile() {
        runCatching { file?.close() }
        file = null
    }

    /** Sends timeshift addresses to the recording and everything else to the network. */
    private class Routing(private val local: DataSource, private val network: DataSource) : DataSource by network {
        private var active: DataSource? = null

        override fun open(dataSpec: DataSpec): Long {
            val target = if (dataSpec.uri.scheme == LiveTimeshift.SCHEME) local else network
            active = target
            return target.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            (active ?: throw IOException("Not opened")).read(buffer, offset, length)

        override fun getUri(): Uri? = active?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

        override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
            local.addTransferListener(transferListener)
            network.addTransferListener(transferListener)
        }

        override fun close() {
            active?.close()
            active = null
        }
    }

    private companion object {
        const val WAIT_MS = 250L
        /** No new data for this long: the channel has stopped; let the player's recovery take over. */
        const val STALL_MS = 15_000L
    }
}
