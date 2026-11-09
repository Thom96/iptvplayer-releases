package nl.iptvplayer.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Details from TMDB (via the Gallo server, see `web/app/api/meta`). */
data class TmdbMeta(
    val overview: String?,
    val poster: String?,
    val backdrop: String?,
    val rating: Double?,
    val genres: List<String>,
)

/**
 * Fetches better posters, descriptions in the app language and ratings from TMDB through the Gallo server, which
 * holds the API key and caches every title. Any failure returns null, and the screen keeps the provider's data.
 */
class MetadataClient(private val http: OkHttpClient, private val baseUrl: String = LicenseClient.SERVER) {
    private val cache = object : LinkedHashMap<String, TmdbMeta?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TmdbMeta?>) = size > 200
    }

    suspend fun movie(tmdbId: Int?, title: String, year: Int?, language: String) = lookup("movie", tmdbId, title, year, language)

    suspend fun series(tmdbId: Int?, title: String, year: Int?, language: String) = lookup("tv", tmdbId, title, year, language)

    private suspend fun lookup(kind: String, tmdbId: Int?, title: String, year: Int?, language: String): TmdbMeta? {
        val key = "$kind|$tmdbId|$title|$year|$language"
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val url = "$baseUrl/api/meta".toHttpUrl().newBuilder()
                    .addQueryParameter("kind", kind)
                    .addQueryParameter("title", title)
                    .addQueryParameter("lang", language)
                    .apply {
                        tmdbId?.let { addQueryParameter("tmdb", it.toString()) }
                        year?.let { addQueryParameter("year", it.toString()) }
                    }
                    .build()
                http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    parse(response.body?.string().orEmpty())
                }
            }.getOrNull()
        }
        synchronized(cache) { cache[key] = result }
        return result
    }

    companion object {
        fun parse(body: String): TmdbMeta? {
            val o = JSONObject(body)
            if (!o.optBoolean("found")) return null
            fun text(name: String) = if (o.isNull(name)) null else o.optString(name).takeIf { it.isNotBlank() }
            val genres = o.optJSONArray("genres")
            return TmdbMeta(
                overview = text("overview"),
                poster = text("poster"),
                backdrop = text("backdrop"),
                rating = o.optDouble("rating").takeIf { !it.isNaN() && it > 0 },
                genres = (0 until (genres?.length() ?: 0)).mapNotNull { genres?.optString(it)?.takeIf(String::isNotBlank) },
            )
        }
    }
}

/** TMDB first (better images, description in the app language), the provider's data as fallback. */
fun MovieInfo.withTmdb(meta: TmdbMeta?): MovieInfo = if (meta == null) this else copy(
    plot = meta.overview ?: plot,
    genre = genre ?: meta.genres.takeIf { it.isNotEmpty() }?.take(3)?.joinToString(", "),
    rating = meta.rating ?: rating,
    backdrop = meta.backdrop ?: backdrop,
    poster = meta.poster ?: poster,
)

fun SeriesInfo.withTmdb(meta: TmdbMeta?): SeriesInfo = if (meta == null) this else copy(
    plot = meta.overview ?: plot,
    genre = genre ?: meta.genres.takeIf { it.isNotEmpty() }?.take(3)?.joinToString(", "),
    rating = meta.rating ?: rating,
    backdrop = meta.backdrop ?: backdrop,
    poster = meta.poster ?: poster,
)
