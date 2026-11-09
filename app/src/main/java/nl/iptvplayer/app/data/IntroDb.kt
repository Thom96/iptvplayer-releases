package nl.iptvplayer.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Parts of an episode that can be skipped. */
enum class SegmentType { RECAP, INTRO, CREDITS }

/** A part of an episode; [endMs] null = until the end. */
data class SkipSegment(val type: SegmentType, val startMs: Long, val endMs: Long?)

/**
 * Intro, recap and credits times from TheIntroDB (community database, looked up by TMDB id of the
 * series plus season and episode). Lookups need no account. Unknown episodes or a network error give an
 * empty list: the player then uses what it learned itself, or its own guess.
 */
class IntroDb(http: OkHttpClient) {

    private val http = http.newBuilder()
        // Without the VLC user agent meant for IPTV servers: this request is from Gallo itself.
        .apply { interceptors().clear() }
        // A skip button that comes too late is useless: give up quickly.
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    /** Results per episode for this app session, including "nothing known". */
    private val cache = object : LinkedHashMap<String, List<SkipSegment>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SkipSegment>>?) = size > 200
    }

    suspend fun episode(tmdbId: Int, season: Int, episode: Int): List<SkipSegment> {
        val key = "$tmdbId:$season:$episode"
        synchronized(cache) { cache[key] }?.let { return it }
        val result = withContext(Dispatchers.IO) {
            val url = BASE_URL.toHttpUrl().newBuilder()
                .addPathSegment("media")
                .addQueryParameter("tmdb_id", tmdbId.toString())
                .addQueryParameter("season", season.toString())
                .addQueryParameter("episode", episode.toString())
                .build()
            val request = Request.Builder().url(url).header("User-Agent", "Gallo").build()
            runCatching {
                http.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> parseIntroDbMedia(response.body?.string().orEmpty())
                        // Not in the database: remember that. Rate limit or server error: try again later.
                        response.code == 404 -> emptyList()
                        else -> null
                    }
                }
            }.getOrNull()
        }
        if (result != null) synchronized(cache) { cache[key] = result }
        return result.orEmpty()
    }

    companion object {
        const val BASE_URL = "https://api.theintrodb.org/v4"
    }
}

/**
 * Reads a `/media` answer: `{"intro": [{"start_ms": 0, "end_ms": 85000}], "credits": [...]}`.
 * A missing start means "from the beginning", a missing end "until the end".
 */
fun parseIntroDbMedia(json: String): List<SkipSegment> = runCatching {
    val root = JSONObject(json)
    listOf("recap" to SegmentType.RECAP, "intro" to SegmentType.INTRO, "credits" to SegmentType.CREDITS)
        .flatMap { (name, type) ->
            val arr = root.optJSONArray(name) ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val start = if (o.isNull("start_ms")) 0L else o.optLong("start_ms", 0L)
                val end = if (o.isNull("end_ms")) null else o.optLong("end_ms")
                SkipSegment(type, start, end).takeIf { end == null || end > start }
            }
        }
        .sortedBy { it.startMs }
}.getOrDefault(emptyList())
