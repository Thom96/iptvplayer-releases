package nl.iptvplayer.app.data

enum class Kind { LIVE, MOVIE, SERIES, EPISODE, CATCHUP }

data class Credentials(val server: String, val username: String, val password: String)

data class Category(val id: String, val name: String)

/** A channel, movie or series from the server's lists. */
class MediaItem(
    val kind: Kind,
    val id: Int,
    val name: String,
    val image: String?,
    val categoryId: String?,
    /** Moment it was added (epoch seconds), 0 if unknown. */
    val added: Long,
    /** Rating on a scale of 0-10, 0 if unknown. */
    val rating: Double,
    /** File extension of movies, e.g. "mkv". */
    val ext: String?,
    val year: Int?,
    /** The provider's channel number (live only), 0 if unknown. */
    val num: Int = 0,
    /** Number of catch-up days (tv_archive), 0 = not available. */
    val archiveDays: Int = 0,
    /** Series only: the provider's list already contains description, genre and wide image. */
    val plot: String? = null,
    val genre: String? = null,
    val backdrop: String? = null,
) {
    val key: String = keyOf(kind, id)

    /**
     * Clean title for the screen, e.g. "The Matrix" instead of "NL - The Matrix (1999)".
     * Computed once (only for titles that are actually shown): cleaning costs four regex patterns.
     */
    val title: String by lazy(LazyThreadSafetyMode.NONE) { TextMatch.displayTitle(name) }

    /**
     * Normalized name for searching. Deliberately the only thing we store per title: derived fields
     * (words, cleaned title) for all 200,000 titles took over 100 MB and made the TV slow.
     */
    internal val norm: String by lazy(LazyThreadSafetyMode.PUBLICATION) { TextMatch.normalize(name) }

    companion object {
        fun keyOf(kind: Kind, id: Int) = "${kind.name}:$id"
    }
}

class Library(
    val liveCategories: List<Category>,
    val movieCategories: List<Category>,
    val seriesCategories: List<Category>,
    val live: List<MediaItem>,
    val movies: List<MediaItem>,
    val series: List<MediaItem>,
    val updatedAt: Long,
) {
    val byKey: Map<String, MediaItem> = buildMap {
        live.forEach { put(it.key, it) }
        movies.forEach { put(it.key, it) }
        series.forEach { put(it.key, it) }
    }

    /** Prepare in the background what the screens need, so that it doesn't happen while drawing. */
    fun warmUp() {
        newestMovies
        newestSeries
        for (list in listOf(movies, series, live)) for (item in list) item.norm
    }

    val newestMovies: List<MediaItem> by lazy { movies.sortedByDescending { it.added }.take(40) }
    val newestSeries: List<MediaItem> by lazy { series.sortedByDescending { it.added }.take(40) }

    /** Language labels with their number of titles, for the language filter. Most common first. */
    val languageCounts: List<Pair<String, Int>> by lazy {
        val counts = HashMap<String, Int>()
        for ((cats, items) in listOf(liveCategories to live, movieCategories to movies, seriesCategories to series)) {
            val catTag = cats.associate { it.id to TextMatch.languageTag(it.name) }
            items.groupingBy { it.categoryId }.eachCount().forEach { (catId, n) ->
                catTag[catId]?.let { counts[it] = (counts[it] ?: 0) + n }
            }
        }
        counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    /** Only titles and categories in the chosen languages; titles without a language label always stay visible. */
    fun filtered(languages: Set<String>): Library {
        if (languages.isEmpty()) return this
        fun keep(tag: String?) = tag == null || tag in languages
        fun filterKind(cats: List<Category>, items: List<MediaItem>): Pair<List<Category>, List<MediaItem>> {
            val catTag = cats.associate { it.id to TextMatch.languageTag(it.name) }
            val keptItems = items.filter { keep(catTag[it.categoryId] ?: TextMatch.languageTag(it.name)) }
            return cats.filter { keep(catTag[it.id]) } to keptItems
        }
        val (lc, l) = filterKind(liveCategories, live)
        val (mc, m) = filterKind(movieCategories, movies)
        val (sc, s) = filterKind(seriesCategories, series)
        return Library(lc, mc, sc, l, m, s, updatedAt)
    }

    fun categories(kind: Kind) = when (kind) {
        Kind.LIVE -> liveCategories
        Kind.MOVIE -> movieCategories
        else -> seriesCategories
    }

    fun items(kind: Kind) = when (kind) {
        Kind.LIVE -> live
        Kind.MOVIE -> movies
        else -> series
    }
}

data class AccountInfo(
    val status: String,
    /** Expiry date in epoch seconds, null = unlimited. */
    val expiresAt: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val allowedFormats: List<String>,
)

data class MovieInfo(
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val releaseDate: String?,
    val durationSecs: Int?,
    val rating: Double?,
    val backdrop: String?,
    val poster: String?,
    val ext: String?,
    val tmdbId: Int? = null,
)

data class Episode(
    val id: Int,
    val season: Int,
    val number: Int,
    val title: String,
    val ext: String,
    val image: String?,
    val plot: String?,
    val durationSecs: Int?,
) {
    val key: String get() = MediaItem.keyOf(Kind.EPISODE, id)
}

/** Extra information for the expanded card (Netflix style). */
data class ItemDetails(
    val plot: String?,
    val genre: String?,
    val backdrop: String?,
    val year: Int?,
    val durationSecs: Int?,
    val rating: Double?,
)

/** One programme from the TV guide; times in epoch milliseconds. */
data class EpgEntry(val title: String, val description: String?, val start: Long, val end: Long) {
    fun isNow(now: Long) = now in start until end
    fun fraction(now: Long) = if (end > start) ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f) else 0f
}

data class Season(val number: Int, val name: String, val episodes: List<Episode>)

data class SeriesInfo(
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val releaseDate: String?,
    val rating: Double?,
    val backdrop: String?,
    val poster: String?,
    val seasons: List<Season>,
    val tmdbId: Int? = null,
)

/** Watch progress of a movie or episode, for "Continue watching". */
data class Progress(
    val key: String,
    val title: String,
    val subtitle: String?,
    val image: String?,
    val ext: String?,
    val seriesId: Int?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** Programme from a channel's archive, for catch-up. */
data class CatchupEntry(
    val title: String,
    val description: String?,
    /** Epoch milliseconds. */
    val start: Long,
    val end: Long,
    /** Start time as the server writes it ("2026-10-24 13:30:00", local server time), needed for the URL. */
    val serverStart: String,
    val hasArchive: Boolean,
)
