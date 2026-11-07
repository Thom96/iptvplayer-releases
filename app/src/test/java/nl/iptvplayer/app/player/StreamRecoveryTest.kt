package nl.iptvplayer.app.player

import androidx.media3.common.PlaybackException
import nl.iptvplayer.app.player.StreamRecovery.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamRecoveryTest {
    private val network = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
    private val decoder = PlaybackException.ERROR_CODE_DECODING_FAILED

    @Test
    fun liveTriesOtherFormatOnceThenRetriesThreeTimesSlower() {
        val r = StreamRecovery()
        assertEquals(Action.UseFallback("alt"), r.onError("LIVE:1", "alt", live = true, errorCode = decoder))
        assertEquals(Action.Retry(2_000, false), r.onError("LIVE:1", "alt", live = true, errorCode = decoder))
        assertEquals(Action.Retry(4_000, false), r.onError("LIVE:1", "alt", live = true, errorCode = decoder))
        assertEquals(Action.Retry(6_000, false), r.onError("LIVE:1", "alt", live = true, errorCode = decoder))
        assertEquals(Action.GiveUp, r.onError("LIVE:1", "alt", live = true, errorCode = decoder))
    }

    @Test
    fun pictureAgainGivesAllRetriesBackButNotTheFallback() {
        val r = StreamRecovery()
        r.onError("LIVE:1", "alt", live = true, errorCode = network)
        repeat(3) { r.onError("LIVE:1", "alt", live = true, errorCode = network) }
        r.onPlaying()
        assertEquals(Action.Retry(2_000, false), r.onError("LIVE:1", "alt", live = true, errorCode = network))
    }

    @Test
    fun choosingTheChannelAgainAllowsTheFallbackAgain() {
        val r = StreamRecovery()
        r.onError("LIVE:1", "alt", live = true, errorCode = network)
        r.reset("LIVE:1")
        assertEquals(Action.UseFallback("alt"), r.onError("LIVE:1", "alt", live = true, errorCode = network))
    }

    @Test
    fun behindLiveWindowJumpsToLiveEdgeImmediately() {
        val r = StreamRecovery()
        assertEquals(
            Action.Retry(0, toLiveEdge = true),
            r.onError("LIVE:1", "alt", live = true, errorCode = PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW),
        )
    }

    @Test
    fun moviesRetryOnlyNetworkErrors() {
        val r = StreamRecovery()
        assertEquals(Action.GiveUp, r.onError("MOVIE:1", null, live = false, errorCode = decoder))
        assertEquals(Action.Retry(2_000, false), r.onError("MOVIE:1", null, live = false, errorCode = network))
        assertEquals(Action.Retry(4_000, false), r.onError("MOVIE:1", null, live = false, errorCode = network))
        assertEquals(Action.Retry(6_000, false), r.onError("MOVIE:1", null, live = false, errorCode = network))
        assertEquals(Action.GiveUp, r.onError("MOVIE:1", null, live = false, errorCode = network))
    }
}
