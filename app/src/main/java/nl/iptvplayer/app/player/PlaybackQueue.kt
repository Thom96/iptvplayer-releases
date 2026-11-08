package nl.iptvplayer.app.player

import nl.iptvplayer.app.data.Kind
import org.json.JSONArray
import org.json.JSONObject

class PlayItem(
    val key: String,
    val kind: Kind,
    val url: String,
    val title: String,
    val subtitle: String? = null,
    val image: String? = null,
    val ext: String? = null,
    val seriesId: Int? = null,
    /** Alternative URL if the first doesn't work (live: .ts instead of .m3u8). */
    val fallbackUrl: String? = null,
)

/**
 * Info panel when opening a series (like Netflix): the episode is already playing, on the left are
 * title, info and buttons. Without any action the panel disappears after 10 seconds.
 */
class SeriesIntro(
    val seriesKey: String,
    val title: String,
    val meta: String,
    val plot: String?,
    val cast: String?,
    /** E.g. "Season 1: Play episode 1" or "Continue watching: S2 E3". */
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

// ---------- Saving, for when Android closes the app in the background ----------

/**
 * The queue as JSON. The player saves it in the private cache when it goes to the background; if Android
 * then closes the app, the player still knows what it was playing when it comes back.
 */
fun PlaybackQueue.toJson(): String = JSONObject().apply {
    put("startIndex", startIndex)
    put("startPositionMs", startPositionMs)
    put("items", JSONArray().apply {
        items.forEach { i ->
            put(JSONObject().apply {
                put("key", i.key)
                put("kind", i.kind.name)
                put("url", i.url)
                put("title", i.title)
                i.subtitle?.let { put("subtitle", it) }
                i.image?.let { put("image", it) }
                i.ext?.let { put("ext", it) }
                i.seriesId?.let { put("seriesId", it) }
                i.fallbackUrl?.let { put("fallbackUrl", it) }
            })
        }
    })
    intro?.let { s ->
        put("intro", JSONObject().apply {
            put("seriesKey", s.seriesKey)
            put("title", s.title)
            put("meta", s.meta)
            s.plot?.let { put("plot", it) }
            s.cast?.let { put("cast", it) }
            put("playLabel", s.playLabel)
        })
    }
}.toString()

/** Reads [toJson] back; null if the file is unreadable or empty. */
fun playbackQueueFromJson(json: String): PlaybackQueue? = runCatching {
    val o = JSONObject(json)
    val arr = o.getJSONArray("items")
    fun JSONObject.textOrNull(name: String) = if (has(name)) getString(name) else null
    val items = List(arr.length()) { n ->
        val i = arr.getJSONObject(n)
        PlayItem(
            key = i.getString("key"),
            kind = Kind.valueOf(i.getString("kind")),
            url = i.getString("url"),
            title = i.getString("title"),
            subtitle = i.textOrNull("subtitle"),
            image = i.textOrNull("image"),
            ext = i.textOrNull("ext"),
            seriesId = if (i.has("seriesId")) i.getInt("seriesId") else null,
            fallbackUrl = i.textOrNull("fallbackUrl"),
        )
    }
    val intro = o.optJSONObject("intro")?.let { s ->
        SeriesIntro(
            seriesKey = s.getString("seriesKey"),
            title = s.getString("title"),
            meta = s.getString("meta"),
            plot = s.textOrNull("plot"),
            cast = s.textOrNull("cast"),
            playLabel = s.getString("playLabel"),
        )
    }
    PlaybackQueue(items, o.getInt("startIndex"), o.getLong("startPositionMs"), intro).takeIf { items.isNotEmpty() }
}.getOrNull()
