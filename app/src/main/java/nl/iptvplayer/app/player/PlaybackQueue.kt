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

// ---------- Bewaren, voor als Android de app op de achtergrond afsluit ----------

/**
 * De wachtrij als JSON. De speler bewaart hem in de privé-cache als hij naar de achtergrond gaat; sluit Android
 * de app daarna af, dan weet de speler bij terugkeer nog wat hij speelde.
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

/** Leest [toJson] terug; null als het bestand onleesbaar of leeg is. */
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
