package nl.iptvplayer.app.player

import nl.iptvplayer.app.data.SegmentType
import nl.iptvplayer.app.data.SkipSegment

/** What the skip button does right now. */
data class SkipAction(val type: SegmentType, val targetMs: Long)

/** Without known times: in the first minutes of an episode, "Skip intro" jumps this far ahead. */
internal const val GENERIC_SKIP_MS = 85_000L
private const val GENERIC_WINDOW_MS = 180_000L

/** The button disappears this long before the end of a segment: then skipping is no use any more. */
private const val SEGMENT_TAIL_MS = 2_000L

/**
 * The skip button at [positionMs]. Order: times from the database, then the intro the app learned for
 * this series, then (episodes only) a jump of 85 s in the first three minutes.
 */
fun skipActionAt(
    positionMs: Long,
    durationMs: Long,
    segments: List<SkipSegment>,
    learnedIntro: SkipSegment?,
    isEpisode: Boolean,
): SkipAction? {
    if (durationMs <= 0) return null
    fun SkipSegment.end() = (endMs ?: durationMs).coerceAtMost(durationMs)

    segments.firstOrNull { (it.type == SegmentType.INTRO || it.type == SegmentType.RECAP) && positionMs in it.startMs until it.end() - SEGMENT_TAIL_MS }
        ?.let { return SkipAction(it.type, it.end()) }

    // A known intro elsewhere in this episode: don't guess.
    if (segments.any { it.type == SegmentType.INTRO }) return null

    learnedIntro?.let { l ->
        return if (positionMs in (l.startMs - 1_000).coerceAtLeast(0) until l.end() - SEGMENT_TAIL_MS) {
            SkipAction(SegmentType.INTRO, l.end())
        } else {
            null
        }
    }

    if (isEpisode && positionMs < GENERIC_WINDOW_MS && durationMs > 10 * 60_000L) {
        return SkipAction(SegmentType.INTRO, (positionMs + GENERIC_SKIP_MS).coerceAtMost(durationMs))
    }
    return null
}

/**
 * How long before the end the "Next episode" prompt appears: at the start of the credits from the database,
 * otherwise a guess (last 5%, min. 30 s, max. 90 s). The prompt always gets at least [minLeadMs], so the
 * countdown fits.
 */
fun creditsLeadMs(durationMs: Long, segments: List<SkipSegment>, minLeadMs: Long = 0): Long {
    val guess = (durationMs * 0.05).toLong().coerceIn(30_000, 90_000)
    // Credits in the first half are something else (e.g. opening credits): ignore them.
    val known = segments
        .filter { it.type == SegmentType.CREDITS && it.startMs >= durationMs / 2 && it.startMs < durationMs }
        .minOfOrNull { durationMs - it.startMs }
    return maxOf(known ?: guess, minLeadMs)
}

/**
 * Learns where the intro of a series is from the viewer's own seeking: forward from somewhere in the first
 * minutes, by 20 s to 4 min. Quick successive presses (10 s or 30 s at a time, or a small step back after
 * overshooting) count as one jump.
 */
class IntroLearner(private val onLearned: (startMs: Long, endMs: Long) -> Unit) {
    private var chainStart = -1L
    private var chainEnd = -1L
    private var lastSeekAt = 0L

    /** A seek within the same episode, from [fromMs] to [toMs], at wall-clock time [now]. */
    fun onSeek(fromMs: Long, toMs: Long, now: Long) {
        val continues = chainStart >= 0 && now - lastSeekAt <= CHAIN_GAP_MS && kotlin.math.abs(fromMs - chainEnd) <= CHAIN_SLACK_MS
        if (!continues) chainStart = fromMs
        chainEnd = toMs
        lastSeekAt = now
        val jump = chainEnd - chainStart
        if (chainStart <= LEARN_WINDOW_MS && jump in MIN_INTRO_MS..MAX_INTRO_MS) onLearned(chainStart, chainEnd)
    }

    /** Another episode: start over. */
    fun reset() {
        chainStart = -1
        chainEnd = -1
    }

    private companion object {
        const val CHAIN_GAP_MS = 4_000L
        const val CHAIN_SLACK_MS = 5_000L
        const val LEARN_WINDOW_MS = 6 * 60_000L
        const val MIN_INTRO_MS = 20_000L
        const val MAX_INTRO_MS = 240_000L
    }
}
