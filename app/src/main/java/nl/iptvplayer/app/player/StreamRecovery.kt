package nl.iptvplayer.app.player

import androidx.media3.common.PlaybackException

/**
 * What to do when a stream gives an error; shared by the Live tab and the player page.
 *
 * - Live: first try the other format (ts <-> m3u8), then restart a few times
 *   (network stall), each time a bit later. Too far behind the broadcast: back to the live edge.
 * - Movies, episodes and catch-up: only retry on network errors, from the same position.
 *
 * Once there is a picture ([onPlaying]) counting starts over. [reset] on a deliberately chosen new channel or title.
 */
class StreamRecovery(private val maxRetries: Int = 3) {

    sealed interface Action {
        /** Restart with this URL. */
        data class UseFallback(val url: String) : Action
        /** Prepare again after [delayMs]; with [toLiveEdge] first go to the live edge. */
        data class Retry(val delayMs: Long, val toLiveEdge: Boolean) : Action
        /** Give up and show the error. */
        data object GiveUp : Action
    }

    private val usedFallback = HashSet<String>()
    private var retries = 0

    /** A channel or title that was just chosen: fallback and attempts start over. */
    fun reset(key: String) {
        usedFallback.remove(key)
        retries = 0
    }

    /** There is a picture again: the next stall gets all attempts again. */
    fun onPlaying() {
        retries = 0
    }

    fun onError(key: String, fallbackUrl: String?, live: Boolean, errorCode: Int): Action {
        val behindLive = errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
        if (live && !behindLive && fallbackUrl != null && usedFallback.add(key)) return Action.UseFallback(fallbackUrl)
        val retryable = live || isNetworkError(errorCode)
        if (!retryable || retries >= maxRetries) return Action.GiveUp
        retries++
        return Action.Retry(delayMs = if (behindLive) 0L else 2_000L * retries, toLiveEdge = behindLive)
    }

    companion object {
        /** Errors while loading (no connection, time-out, server error): 2000–2999 in Media3. */
        fun isNetworkError(errorCode: Int) = errorCode in 2000..2999
    }
}
