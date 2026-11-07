package nl.iptvplayer.app.player

import androidx.media3.common.PlaybackException

/**
 * Wat te doen als een stream een fout geeft; gedeeld door de Live-tab en de spelerpagina.
 *
 * - Live: eerst het andere formaat proberen (ts <-> m3u8), daarna een paar keer opnieuw starten
 *   (netwerkhapering), steeds iets later. Te ver achter op de uitzending: terug naar het live-punt.
 * - Films, afleveringen en terugkijken: alleen bij netwerkfouten opnieuw proberen, vanaf dezelfde positie.
 *
 * Na beeld ([onPlaying]) begint het tellen opnieuw. [reset] bij een bewust gekozen nieuwe zender of titel.
 */
class StreamRecovery(private val maxRetries: Int = 3) {

    sealed interface Action {
        /** Opnieuw starten met deze URL. */
        data class UseFallback(val url: String) : Action
        /** Na [delayMs] opnieuw voorbereiden; met [toLiveEdge] eerst naar het live-punt. */
        data class Retry(val delayMs: Long, val toLiveEdge: Boolean) : Action
        /** Opgeven en de fout tonen. */
        data object GiveUp : Action
    }

    private val usedFallback = HashSet<String>()
    private var retries = 0

    /** Een zender of titel die net gekozen is: fallback en pogingen opnieuw. */
    fun reset(key: String) {
        usedFallback.remove(key)
        retries = 0
    }

    /** Er is weer beeld: een volgende hapering krijgt weer alle pogingen. */
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
        /** Fouten bij het binnenhalen (geen verbinding, time-out, serverfout): 2000–2999 in Media3. */
        fun isNetworkError(errorCode: Int) = errorCode in 2000..2999
    }
}
