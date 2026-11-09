package nl.iptvplayer.app.player.timeshift

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Pause and rewind for live TV, for one player.
 *
 * A live channel is played via an address like `timeshift://live?u=<stream url>`: opening it starts a
 * [TimeshiftRecorder] for that channel (or reuses the one that is running) and plays from its newest piece,
 * so live starts as fast as before. Rewinding replaces the item with an address for an older piece
 * (`timeshift://seg?u=…&s=<piece>`); "back to live" goes to the newest piece again.
 *
 * Only MPEG-TS streams are recorded; other formats play directly, without rewinding.
 */
@UnstableApi
class LiveTimeshift(
    context: Context,
    private val http: OkHttpClient,
    private val userAgent: String,
    private val maxDurationMs: Long,
) {
    /** What the controls show: how far behind live, and where in the recorded window playback is. */
    data class State(val behindMs: Long, val windowMs: Long, val positionMs: Long) {
        val isLive: Boolean get() = behindMs < LIVE_MARGIN_MS
    }

    private val root = File(context.cacheDir, "timeshift")
    @Volatile private var recorder: TimeshiftRecorder? = null
    /** Recording time at which the current playback started (start of the piece that was opened). */
    @Volatile private var originMs = 0L
    @Volatile private var originSeq = 0
    private var nonce = 0

    init {
        // Leftovers from a previous session (app closed by the system); active recordings change every few seconds.
        val cutoff = System.currentTimeMillis() - 10 * 60_000
        root.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    }

    /** Address to play [url] through the recording, or null if this format can't be recorded. */
    fun uriFor(url: String): Uri? =
        if (url.substringBefore('?').endsWith(".ts")) liveUri(url) else null

    fun dataSourceFactory(upstream: androidx.media3.datasource.DataSource.Factory) =
        TimeshiftDataSource.Factory(this, upstream)

    /** Called by [TimeshiftDataSource]: the recording to read and the piece to start at. */
    @Synchronized
    internal fun openForPlayback(uri: Uri, fromStart: Boolean): Pair<TimeshiftRecorder, Int> {
        val url = uri.getQueryParameter("u") ?: throw IOException("No stream address")
        var rec = recorder
        if (rec == null || rec.url != url || !rec.isHealthy) {
            rec?.stop()
            rec = TimeshiftRecorder(http, url, userAgent, File(root, UUID.randomUUID().toString()), maxDurationMs)
            rec.start()
            recorder = rec
        }
        if (!fromStart) return rec to originSeq
        val requested = uri.getQueryParameter("s")?.toIntOrNull()
        val segment = if (uri.authority == SEGMENT && requested != null) rec.segment(requested) ?: rec.oldest() else rec.newest()
        originSeq = segment?.seq ?: 0
        originMs = segment?.startMs ?: 0L
        return rec to originSeq
    }

    /** Current state, or null if the current item isn't played through a recording. */
    fun state(player: Player): State? {
        val rec = recording(player) ?: return null
        val oldest = rec.oldest()?.startMs ?: return null
        val edge = rec.liveEdgeMs()
        val position = (originMs + player.currentPosition).coerceIn(oldest, edge)
        return State(behindMs = edge - position, windowMs = edge - oldest, positionMs = position - oldest)
    }

    /** Rewinds (negative) or forwards; forwarding up to live goes back to live. */
    fun seekBy(player: Player, deltaMs: Long) {
        val rec = recording(player) ?: return
        val target = originMs + player.currentPosition + deltaMs
        if (target >= rec.liveEdgeMs() - LIVE_MARGIN_MS) {
            if (state(player)?.isLive == false) goLive(player)
            return
        }
        val segment = rec.segmentAt(target) ?: return
        replace(player, segmentUri(rec.url, segment.seq))
    }

    fun goLive(player: Player) {
        val rec = recording(player) ?: return
        replace(player, liveUri(rec.url))
    }

    /** Stops the recording (app in the background); playing again starts a new one. */
    fun stopRecording() {
        recorder?.stop()
        recorder = null
    }

    fun release() = stopRecording()

    private fun recording(player: Player): TimeshiftRecorder? {
        val uri = player.currentMediaItem?.localConfiguration?.uri ?: return null
        if (uri.scheme != SCHEME) return null
        val rec = recorder ?: return null
        return rec.takeIf { it.url == uri.getQueryParameter("u") }
    }

    private fun replace(player: Player, uri: Uri) {
        val item = player.currentMediaItem ?: return
        val index = player.currentMediaItemIndex
        player.replaceMediaItem(index, item.buildUpon().setUri(uri).build())
        player.seekToDefaultPosition(index)
        player.prepare()
    }

    private fun liveUri(url: String): Uri = Uri.Builder().scheme(SCHEME).authority(LIVE)
        .appendQueryParameter("u", url)
        .appendQueryParameter("n", (nonce++).toString()) // a new address forces a fresh start
        .build()

    private fun segmentUri(url: String, seq: Int): Uri = Uri.Builder().scheme(SCHEME).authority(SEGMENT)
        .appendQueryParameter("u", url)
        .appendQueryParameter("s", seq.toString())
        .appendQueryParameter("n", (nonce++).toString())
        .build()

    companion object {
        const val SCHEME = "timeshift"
        private const val LIVE = "live"
        private const val SEGMENT = "seg"
        /** Closer to live than this counts as live (the start of a channel is always a few seconds behind). */
        const val LIVE_MARGIN_MS = 12_000L
        const val STEP_MS = 10_000L

        fun isTimeshiftUri(uri: Uri?) = uri?.scheme == SCHEME
    }
}

/** Media item for a live channel: through the recording when possible (MPEG-TS), otherwise directly. */
@UnstableApi
fun LiveTimeshift?.liveMediaItem(builder: MediaItem.Builder, url: String): MediaItem.Builder {
    val uri = this?.uriFor(url) ?: return builder.setUri(url)
    return builder.setUri(uri).setMimeType(androidx.media3.common.MimeTypes.VIDEO_MP2T)
}
