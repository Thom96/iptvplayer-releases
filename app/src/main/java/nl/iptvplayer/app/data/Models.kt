package nl.iptvplayer.app.data

enum class Kind { LIVE, MOVIE, SERIES, EPISODE, CATCHUP }

data class Credentials(val server: String, val username: String, val password: String)

data class Category(val id: String, val name: String)

/** Een zender, film of serie uit de lijsten van de server. */
class MediaItem(
    val kind: Kind,
    val id: Int,
    val name: String,
    val image: String?,
    val categoryId: String?,
    /** Moment van toevoegen (epoch seconden), 0 als onbekend. */
    val added: Long,
    /** Beoordeling op schaal 0-10, 0 als onbekend. */
    val rating: Double,
    /** Bestandsextensie van films, bijv. "mkv". */
    val ext: String?,
    val year: Int?,
    /** Zendernummer van de provider (alleen live), 0 als onbekend. */
    val num: Int = 0,
    /** Aantal dagen terugkijken (tv_archive), 0 = niet beschikbaar. */
    val archiveDays: Int = 0,
    /** Alleen series: de lijst van de provider bevat al beschrijving, genre en brede afbeelding. */
    val plot: String? = null,
    val genre: String? = null,
    val backdrop: String? = null,
) {
    val key: String = keyOf(kind, id)

    /**
     * Nette titel voor op het scherm, bijv. "The Matrix" i.p.v. "NL - The Matrix (1999)".
     * Eén keer berekend (alleen voor titels die echt getoond worden): het opschonen kost vier zoekpatronen.
     */
    val title: String by lazy(LazyThreadSafetyMode.NONE) { TextMatch.displayTitle(name) }

    /**
     * Genormaliseerde naam voor zoeken. Bewust het enige wat we per titel bewaren: afgeleide velden
     * (woorden, opgeschoonde titel) voor alle 200.000 titels kostten ruim 100 MB en maakten de tv traag.
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

    /** Op de achtergrond voorbereiden wat de schermen nodig hebben, zodat dat niet tijdens het tekenen gebeurt. */
    fun warmUp() {
        newestMovies
        newestSeries
        for (list in listOf(movies, series, live)) for (item in list) item.norm
    }

    val newestMovies: List<MediaItem> by lazy { movies.sortedByDescending { it.added }.take(40) }
    val newestSeries: List<MediaItem> by lazy { series.sortedByDescending { it.added }.take(40) }

    /** Taallabels met het aantal titels, voor het taalfilter. Meest voorkomend eerst. */
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

    /** Alleen titels en categorieën in de gekozen talen; titels zonder taallabel blijven altijd zichtbaar. */
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
    /** Verloopdatum in epoch seconden, null = onbeperkt. */
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

/** Extra informatie voor de uitgeklapte kaart (Netflix-stijl). */
data class ItemDetails(
    val plot: String?,
    val genre: String?,
    val backdrop: String?,
    val year: Int?,
    val durationSecs: Int?,
    val rating: Double?,
)

/** Eén programma uit de tv-gids; tijden in epoch milliseconden. */
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
)

/** Kijkvoortgang van een film of aflevering, voor "Verder kijken". */
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

/** Programma uit het archief van een zender, om terug te kijken. */
data class CatchupEntry(
    val title: String,
    val description: String?,
    /** Epoch milliseconden. */
    val start: Long,
    val end: Long,
    /** Begintijd zoals de server hem schrijft ("2026-10-24 13:30:00", lokale servertijd), nodig voor de URL. */
    val serverStart: String,
    val hasArchive: Boolean,
)
