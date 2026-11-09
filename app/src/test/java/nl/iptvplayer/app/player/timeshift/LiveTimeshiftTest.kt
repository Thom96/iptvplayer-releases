package nl.iptvplayer.app.player.timeshift

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Records a made-up channel from a local server and plays it back from the device. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class LiveTimeshiftTest {
    private lateinit var server: ServerSocket
    private lateinit var timeshift: LiveTimeshift
    private val stream = TsTestStream.build(seconds = 40)

    @Before
    fun setUp() {
        server = ServerSocket(0)
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    val out = socket.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\n\r\n".toByteArray())
                    out.write(stream)
                    out.flush()
                    Thread.sleep(30_000) // a live channel doesn't end
                }
            }
        }
        timeshift = LiveTimeshift(ApplicationProvider.getApplicationContext(), OkHttpClient(), "test", maxDurationMs = 10_000)
    }

    @After
    fun tearDown() {
        timeshift.release()
        server.close()
    }

    private val url get() = "http://127.0.0.1:${server.localPort}/live/u/p/1.ts"

    private fun open(): DataSource {
        val uri = timeshift.uriFor(url)!!
        val ds = timeshift.dataSourceFactory(androidx.media3.datasource.FileDataSource.Factory()).createDataSource()
        ds.open(DataSpec(uri))
        return ds
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(20)
        }
    }

    @Test
    fun onlyTransportStreamsAreRecorded() {
        assertNotNull(timeshift.uriFor("http://x/live/u/p/1.ts"))
        assertNull(timeshift.uriFor("http://x/live/u/p/1.m3u8"))
    }

    @Test
    fun playsTheChannelFromTheRecording() {
        val ds = open()
        val buf = ByteArray(188 * 10)
        var read = 0
        while (read < buf.size) read += ds.read(buf, read, buf.size - read)
        assertEquals(0x47, buf[0].toInt())
        assertEquals(0x47, buf[188].toInt())
        ds.close()
    }

    @Test
    fun keepsOnlyTheChosenLengthAndReadersSkipAhead() {
        val ds = open()
        val (rec, _) = timeshift.openForPlayback(timeshift.uriFor(url)!!, fromStart = false)
        // 40 s streamed, 10 s kept: the first pieces are gone.
        waitFor { (rec.oldest()?.startMs ?: 0) >= 28_000 }
        val oldest = rec.oldest()!!
        assertTrue(rec.liveEdgeMs() - oldest.startMs in 10_000..14_000)
        assertTrue(!oldest.file.parentFile!!.list()!!.contains("0.ts"))

        // A reader that was still at the start (paused too long) continues at the oldest piece that is left.
        val buf = ByteArray(188)
        var read = 0
        while (read < buf.size) read += ds.read(buf, read, buf.size - read)
        assertEquals(0x47, buf[0].toInt())
        ds.close()
    }

    @Test
    fun newPiecesStartWithTablesAndAKeyFrame() {
        open().close()
        val (rec, _) = timeshift.openForPlayback(timeshift.uriFor(url)!!, fromStart = false)
        waitFor { (rec.newest()?.seq ?: 0) >= 5 }
        val piece = rec.segment(rec.newest()!!.seq - 1)!!.file.readBytes()
        assertEquals(0, piece[2].toInt()) // PAT
        assertEquals(0x10, piece[188 + 2].toInt()) // PMT
        assertEquals(0x40, piece[2 * 188 + 5].toInt() and 0x40) // key frame
    }
}
