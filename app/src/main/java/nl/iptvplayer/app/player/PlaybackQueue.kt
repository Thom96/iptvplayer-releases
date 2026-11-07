package nl.iptvplayer.app.player

import nl.iptvplayer.app.data.Kind

class PlayItem(
    val key: String,
    val kind: Kind,
    val url: String,
    val title: String,
    val subtitle: String? = null,
    val image: String? = null,
    val ext: String? = null,
    val seriesId: Int? = null,
    /** Alternatieve URL als de eerste niet werkt (live: .ts in plaats van .m3u8). */
    val fallbackUrl: String? = null,
)

/**
 * Informatiepaneel bij het openen van een serie (zoals Netflix): de aflevering speelt al, links staan
 * titel, info en knoppen. Zonder actie verdwijnt het paneel na 10 seconden.
 */
class SeriesIntro(
    val seriesKey: String,
    val title: String,
    val meta: String,
    val plot: String?,
    val cast: String?,
    /** Bijv. "Seizoen 1: Aflevering 1 afspelen" of "Verder kijken: S2 E3". */
    val playLabel: String,
)

class PlaybackQueue(
    val items: List<PlayItem>,
    val startIndex: Int = 0,
    val startPositionMs: Long = 0,
    val intro: SeriesIntro? = null,
) {
    val isLive: Boolean get() = items.firstOrNull()?.kind == Kind.LIVE
}
