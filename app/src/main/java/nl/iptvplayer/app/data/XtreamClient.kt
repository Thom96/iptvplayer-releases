package nl.iptvplayer.app.data

import nl.iptvplayer.app.i18n.S
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicInteger

class AuthException(message: String) : IOException(message)

/** Talks to an Xtream Codes server via player_api.php. */
class XtreamClient(
    val creds: Credentials,
    private val http: OkHttpClient,
    cacheRoot: File,
) {
    private val base: HttpUrl = ("${creds.server}/player_api.php".toHttpUrlOrNull()
        ?: throw IOException(S.invalidServerUrl))
        .newBuilder()
        .addQueryParameter("username", creds.username)
        .addQueryParameter("password", creds.password)
        .build()

    private val cacheDir = File(cacheRoot, "lists-" + (creds.server + "|" + creds.username).hashCode().toUInt().toString(16))

    private val demo = creds.server == DEMO_SERVER

    /**
     * Format for live streams that the server recommends: ts, or m3u8 if ts isn't allowed. Determined at
     * [authenticate]; until then the value from last time (AppViewModel sets it), otherwise ts.
     */
    var liveFormat: String = "ts"

    // ---------- Account ----------

    suspend fun authenticate(): AccountInfo = withContext(Dispatchers.IO) {
        if (demo) return@withContext AccountInfo("Active", null, 2, 0, listOf("m3u8", "ts"))
        val body = getString(base)
        val root = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw AuthException(S.unexpectedResponse)
        }
        val user = root.optJSONObject("user_info")
            ?: throw AuthException(S.wrongCredentials)
        if (user.optInt("auth", 1) == 0) throw AuthException(S.wrongCredentials)
        val status = user.optString("status", "Active")
        if (!status.equals("Active", ignoreCase = true)) throw AuthException(S.accountInactive(status))

        val formats = user.optJSONArray("allowed_output_formats")?.let { arr ->
            List(arr.length()) { arr.optString(it) }
        } ?: emptyList()
        // TS starts faster than HLS on most IPTV servers (measured: ~1 s against ~2 s); HLS only if TS isn't allowed.
        liveFormat = if (formats.isEmpty() || "ts" in formats) "ts" else "m3u8"

        AccountInfo(
            status = status,
            expiresAt = user.optString("exp_date").toLongOrNull()?.takeIf { it > 0 },
            maxConnections = user.optString("max_connections").toIntOrNull(),
            activeConnections = user.optString("active_cons").toIntOrNull(),
            allowedFormats = formats,
        )
    }

    // ---------- Lists ----------

    val hasCache: Boolean get() = demo || LIST_ACTIONS.all { File(cacheDir, "$it.json").exists() }

    val cacheAgeMs: Long
        get() = LIST_ACTIONS.maxOfOrNull { System.currentTimeMillis() - File(cacheDir, "$it.json").lastModified() } ?: Long.MAX_VALUE

    fun clearCache() {
        cacheDir.deleteRecursively()
    }

    /**
     * Loads all categories and streams. With [forceDownload] = false, existing cache files are
     * used; otherwise everything is downloaded again.
     *
     * [onProgress] gets a text and the share of the steps that are done (0..1): every downloaded list and
     * every processed list counts as one step. Called from background threads.
     */
    suspend fun loadLibrary(
        forceDownload: Boolean,
        onProgress: (message: String, fraction: Float) -> Unit = { _, _ -> },
    ): Library = coroutineScope {
        if (demo) return@coroutineScope DemoData.library()
        cacheDir.mkdirs()
        val downloading = forceDownload || !hasCache
        val totalSteps = (if (downloading) 2 else 1) * LIST_ACTIONS.size
        val doneSteps = AtomicInteger(0)
        fun stepDone(message: String) = onProgress(message, doneSteps.incrementAndGet().toFloat() / totalSteps)
        if (downloading) {
            onProgress(S.downloadingLists, 0f)
            LIST_ACTIONS.map { action ->
                async(Dispatchers.IO) { download(action); stepDone(S.downloadingLists) }
            }.forEach { it.await() }
        }
        // Fast path: the already processed lists from index.bin, as long as it is newer than the JSON files.
        if (indexIsFresh()) {
            withContext(Dispatchers.IO) { runCatching { readIndex() }.getOrNull() }?.let { return@coroutineScope it }
        }
        val indexing = S.indexing
        onProgress(indexing, doneSteps.get().toFloat() / totalSteps)
        val library = withContext(Dispatchers.Default) {
            val liveCats = async { parseCategories(file("get_live_categories")).also { stepDone(indexing) } }
            val vodCats = async { parseCategories(file("get_vod_categories")).also { stepDone(indexing) } }
            val seriesCats = async { parseCategories(file("get_series_categories")).also { stepDone(indexing) } }
            val live = async { parseItems(file("get_live_streams"), Kind.LIVE).also { stepDone(indexing) } }
            val movies = async { parseItems(file("get_vod_streams"), Kind.MOVIE).also { stepDone(indexing) } }
            val series = async { parseItems(file("get_series"), Kind.SERIES).also { stepDone(indexing) } }
            Library(
                liveCats.await(), vodCats.await(), seriesCats.await(),
                live.await(), movies.await(), series.await(),
                updatedAt = file("get_vod_streams").lastModified(),
            )
        }
        withContext(Dispatchers.IO) { runCatching { writeIndex(library) } }
        library
    }

    private fun file(action: String) = File(cacheDir, "$action.json")

    // ---------- Index: processed lists in a compact binary format ----------

    private val indexFile get() = File(cacheDir, "index.bin")

    private fun indexIsFresh(): Boolean {
        val idx = indexFile
        return idx.exists() && LIST_ACTIONS.all { file(it).lastModified() <= idx.lastModified() }
    }

    private fun writeIndex(lib: Library) {
        val tmp = File(cacheDir, "index.tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 256 * 1024)).use { out ->
            out.writeInt(INDEX_VERSION)
            out.writeLong(lib.updatedAt)
            for (cats in listOf(lib.liveCategories, lib.movieCategories, lib.seriesCategories)) {
                out.writeInt(cats.size)
                cats.forEach { out.writeUTF(it.id); out.writeUTF(it.name) }
            }
            for (items in listOf(lib.live, lib.movies, lib.series)) {
                out.writeInt(items.size)
                for (it in items) {
                    out.writeInt(it.id)
                    out.writeUTF(it.name)
                    out.writeUTF(it.image ?: "")
                    out.writeUTF(it.categoryId ?: "")
                    out.writeLong(it.added)
                    out.writeDouble(it.rating)
                    out.writeUTF(it.ext ?: "")
                    out.writeInt(it.year ?: 0)
                    out.writeInt(it.num)
                    out.writeInt(it.archiveDays)
                    out.writeUTF(it.plot ?: "")
                    out.writeUTF(it.genre ?: "")
                    out.writeUTF(it.backdrop ?: "")
                }
            }
        }
        indexFile.delete()
        tmp.renameTo(indexFile)
    }

    private fun readIndex(): Library =
        DataInputStream(BufferedInputStream(FileInputStream(indexFile), 256 * 1024)).use { inp ->
            if (inp.readInt() != INDEX_VERSION) throw IOException("Oude index")
            val updatedAt = inp.readLong()
            val cats = List(3) {
                List(inp.readInt()) { Category(inp.readUTF(), inp.readUTF()) }
            }
            val items = listOf(Kind.LIVE, Kind.MOVIE, Kind.SERIES).map { kind ->
                val n = inp.readInt()
                ArrayList<MediaItem>(n).apply {
                    repeat(n) {
                        add(
                            MediaItem(
                                kind = kind,
                                id = inp.readInt(),
                                name = inp.readUTF(),
                                image = inp.readUTF().ifEmpty { null },
                                categoryId = inp.readUTF().ifEmpty { null },
                                added = inp.readLong(),
                                rating = inp.readDouble(),
                                ext = inp.readUTF().ifEmpty { null },
                                year = inp.readInt().takeIf { it != 0 },
                                num = inp.readInt(),
                                archiveDays = inp.readInt(),
                                plot = inp.readUTF().ifEmpty { null },
                                genre = inp.readUTF().ifEmpty { null },
                                backdrop = inp.readUTF().ifEmpty { null },
                            ),
                        )
                    }
                }
            }
            Library(cats[0], cats[1], cats[2], items[0], items[1], items[2], updatedAt)
        }

    private fun download(action: String) {
        val url = base.newBuilder().addQueryParameter("action", action).build()
        val target = file(action)
        val tmp = File(cacheDir, "$action.tmp")
        http.newCall(request(url)).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException(S.serverError(resp.code) + " ($action)")
            val body = resp.body ?: throw IOException(S.emptyResponse + " ($action)")
            tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    private fun parseCategories(file: File): List<Category> {
        val arr = try {
            JSONArray(file.readText())
        } catch (e: Exception) {
            return emptyList()
        }
        return List(arr.length()) { i ->
            val o = arr.optJSONObject(i) ?: return@List null
            val id = o.optString("category_id").takeIf { it.isNotEmpty() } ?: return@List null
            Category(id, o.optString("category_name").trim().ifEmpty { S.unnamedCategory })
        }.filterNotNull()
    }

    /** Streaming parse: the movie list can be tens of MB. */
    private fun parseItems(file: File, kind: Kind): List<MediaItem> {
        val result = ArrayList<MediaItem>()
        JsonReader(InputStreamReader(file.inputStream().buffered(64 * 1024), Charsets.UTF_8)).use { r ->
            r.isLenient = true
            if (r.peek() != JsonToken.BEGIN_ARRAY) return emptyList()
            r.beginArray()
            while (r.hasNext()) {
                if (r.peek() != JsonToken.BEGIN_OBJECT) {
                    r.skipValue(); continue
                }
                var id: Int? = null
                var name: String? = null
                var image: String? = null
                var cat: String? = null
                var added = 0L
                var rating = 0.0
                var ext: String? = null
                var year: Int? = null
                var num = 0
                var archive = false
                var archiveDays = 0
                var plot: String? = null
                var genre: String? = null
                var backdrop: String? = null
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "stream_id", "series_id" -> id = r.looseString()?.toIntOrNull() ?: id
                        "name" -> name = r.looseString()
                        "title" -> r.looseString().let { if (name.isNullOrBlank()) name = it }
                        "stream_icon", "cover" -> image = r.looseString()?.takeIf { it.startsWith("http") }
                        "category_id" -> cat = r.looseString()
                        "added", "last_modified" -> added = maxOf(added, r.looseString()?.toLongOrNull() ?: 0L)
                        "rating" -> r.looseString()?.toDoubleOrNull()?.let { if (it > 0) rating = it }
                        "rating_5based" -> r.looseString()?.toDoubleOrNull()?.let { if (rating == 0.0 && it > 0) rating = it * 2 }
                        "container_extension" -> ext = r.looseString()
                        "num" -> num = r.looseString()?.toIntOrNull() ?: 0
                        "tv_archive" -> archive = r.looseString() == "1"
                        "tv_archive_duration" -> archiveDays = r.looseString()?.toIntOrNull() ?: 0
                        "plot", "description" -> r.looseString()?.trim()?.takeIf { it.isNotEmpty() }?.let { plot = it.take(1500) }
                        "genre" -> genre = r.looseString()?.trim()?.takeIf { it.isNotEmpty() }
                        "backdrop_path" -> backdrop = r.firstUrl()
                        "year", "releaseDate", "release_date" -> r.looseString()?.take(4)?.toIntOrNull()?.let { year = it }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                val n = name?.trim()
                if (id != null && !n.isNullOrEmpty()) {
                    result += MediaItem(kind, id, n, image, cat, added, rating, ext, year ?: TextMatch.yearFrom(n), num, if (archive) archiveDays.coerceAtLeast(1) else 0, plot, genre, backdrop)
                }
            }
            r.endArray()
        }
        return result
    }

    // ---------- Details ----------

    suspend fun movieInfo(id: Int): MovieInfo = withContext(Dispatchers.IO) {
        if (demo) return@withContext DemoData.movieInfo(id)
        val root = JSONObject(getString(base.newBuilder().addQueryParameter("action", "get_vod_info").addQueryParameter("vod_id", id.toString()).build()))
        val info = root.optJSONObject("info") ?: JSONObject()
        val data = root.optJSONObject("movie_data") ?: JSONObject()
        MovieInfo(
            plot = info.text("plot", "description"),
            genre = info.text("genre"),
            cast = info.text("cast", "actors"),
            director = info.text("director"),
            releaseDate = info.text("releasedate", "release_date", "releaseDate"),
            // Less than a minute is a placeholder from the provider (showing "0 min" makes no sense).
            durationSecs = info.optString("duration_secs").toIntOrNull()?.takeIf { it >= 60 },
            rating = info.optString("rating").toDoubleOrNull()?.takeIf { it > 0 },
            backdrop = info.backdrop(),
            poster = info.text("movie_image", "cover_big"),
            ext = data.text("container_extension"),
        )
    }

    suspend fun seriesInfo(id: Int): SeriesInfo = withContext(Dispatchers.IO) {
        if (demo) return@withContext DemoData.seriesInfo(id)
        val root = JSONObject(getString(base.newBuilder().addQueryParameter("action", "get_series_info").addQueryParameter("series_id", id.toString()).build()))
        val info = root.optJSONObject("info") ?: JSONObject()

        val seasonNames = HashMap<Int, String>()
        root.optJSONArray("seasons")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val nr = s.optString("season_number").toIntOrNull() ?: continue
                s.text("name")?.let { seasonNames[nr] = it }
            }
        }

        // "episodes" is usually {"1": [...], "2": [...]}, but some servers send an array of arrays.
        val episodeLists = ArrayList<JSONArray>()
        when (val eps = root.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { k -> eps.optJSONArray(k)?.let(episodeLists::add) }
            is JSONArray -> for (i in 0 until eps.length()) {
                when (val v = eps.opt(i)) {
                    is JSONArray -> episodeLists += v
                    is JSONObject -> episodeLists += JSONArray().put(v)
                }
            }
        }
        val episodes = episodeLists.flatMap { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val e = arr.optJSONObject(i) ?: return@mapNotNull null
                val eInfo = e.optJSONObject("info") ?: JSONObject()
                Episode(
                    id = e.optString("id").toIntOrNull() ?: return@mapNotNull null,
                    season = e.optString("season").toIntOrNull() ?: 1,
                    number = e.optString("episode_num").toIntOrNull() ?: (i + 1),
                    title = e.text("title") ?: S.episodeName(i + 1),
                    ext = e.text("container_extension") ?: "mp4",
                    image = eInfo.text("movie_image", "cover_big"),
                    plot = eInfo.text("plot"),
                    durationSecs = eInfo.optString("duration_secs").toIntOrNull()?.takeIf { it >= 60 },
                )
            }
        }
        val seasons = episodes.groupBy { it.season }.toSortedMap().map { (nr, eps) ->
            Season(nr, seasonNames[nr] ?: S.seasonName(nr), eps.sortedBy { it.number })
        }

        SeriesInfo(
            plot = info.text("plot"),
            genre = info.text("genre"),
            cast = info.text("cast"),
            releaseDate = info.text("releaseDate", "release_date", "releasedate"),
            rating = info.optString("rating").toDoubleOrNull()?.takeIf { it > 0 },
            backdrop = info.backdrop(),
            poster = info.text("cover"),
            seasons = seasons,
        )
    }

    // ---------- TV guide ----------

    /** Current and upcoming programmes of a channel. Titles and descriptions are base64 encoded. */
    suspend fun shortEpg(streamId: Int, limit: Int = 4): List<EpgEntry> = withContext(Dispatchers.IO) {
        if (demo) return@withContext DemoData.epg(streamId)
        val url = base.newBuilder()
            .addQueryParameter("action", "get_short_epg")
            .addQueryParameter("stream_id", streamId.toString())
            .addQueryParameter("limit", limit.toString())
            .build()
        val arr = JSONObject(getString(url)).optJSONArray("epg_listings") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val start = o.optString("start_timestamp").toLongOrNull() ?: return@mapNotNull null
            val end = (o.optString("stop_timestamp").ifEmpty { o.optString("end_timestamp") }).toLongOrNull()
                ?: return@mapNotNull null
            EpgEntry(
                title = decodeBase64(o.optString("title")).ifBlank { S.unknownProgram },
                description = decodeBase64(o.optString("description")).takeIf { it.isNotBlank() },
                start = start * 1000,
                end = end * 1000,
            )
        }.sortedBy { it.start }.fold(mutableListOf<EpgEntry>()) { acc, e ->
            // Some providers send overlapping items; keep one per time slot.
            if (acc.isEmpty() || e.start >= acc.last().end - 60_000) acc += e
            acc
        }
    }

    /** Full guide of a channel including past days, for catch-up. */
    suspend fun archiveEpg(streamId: Int): List<CatchupEntry> = withContext(Dispatchers.IO) {
        if (demo) return@withContext DemoData.archive(streamId)
        val url = base.newBuilder()
            .addQueryParameter("action", "get_simple_data_table")
            .addQueryParameter("stream_id", streamId.toString())
            .build()
        val arr = JSONObject(getString(url)).optJSONArray("epg_listings") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val start = o.optString("start_timestamp").toLongOrNull() ?: return@mapNotNull null
            val end = (o.optString("stop_timestamp").ifEmpty { o.optString("end_timestamp") }).toLongOrNull()
                ?: return@mapNotNull null
            val serverStart = o.optString("start").takeIf { it.length >= 16 } ?: return@mapNotNull null
            CatchupEntry(
                title = decodeBase64(o.optString("title")).ifBlank { S.unknownProgram },
                description = decodeBase64(o.optString("description")).takeIf { it.isNotBlank() },
                start = start * 1000,
                end = end * 1000,
                serverStart = serverStart,
                hasArchive = o.optString("has_archive") == "1",
            )
        }.distinctBy { it.start }.sortedBy { it.start }
    }

    /**
     * Catch-up URL: /timeshift/{user}/{pass}/{duration in minutes}/{start as yyyy-mm-dd:hh-mm}/{id}.ts.
     * The start time must be in the server's time zone; we take it literally from the guide.
     */
    fun catchupUrl(streamId: Int, entry: CatchupEntry): String {
        if (demo) return DemoData.MOVIE_URL
        val minutes = ((entry.end - entry.start + 59_999) / 60_000).coerceAtLeast(1)
        val s = entry.serverStart
        val start = "${s.substring(0, 10)}:${s.substring(11, 13)}-${s.substring(14, 16)}"
        return "${creds.server}/timeshift/${enc(creds.username)}/${enc(creds.password)}/$minutes/$start/$streamId.ts"
    }

    private fun decodeBase64(s: String): String = try {
        String(android.util.Base64.decode(s, android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
    } catch (e: IllegalArgumentException) {
        s // some servers send plain text
    }

    // ---------- Stream URLs ----------

    private fun streamUrl(type: String, id: Int, ext: String) =
        "${creds.server}/$type/${enc(creds.username)}/${enc(creds.password)}/$id.$ext"

    fun liveUrl(id: Int, format: String = liveFormat) =
        if (demo) DemoData.LIVE_URL else streamUrl("live", id, format)

    fun movieUrl(id: Int, ext: String?) =
        if (demo) DemoData.MOVIE_URL else streamUrl("movie", id, ext?.takeIf { it.isNotBlank() } ?: "mp4")

    fun episodeUrl(id: Int, ext: String?) =
        if (demo) DemoData.EPISODE_URL else streamUrl("series", id, ext?.takeIf { it.isNotBlank() } ?: "mp4")

    // ---------- Helpers ----------

    private fun request(url: HttpUrl) = Request.Builder().url(url).build()

    private fun getString(url: HttpUrl): String =
        http.newCall(request(url)).execute().use { resp ->
            // Some providers use their own codes (511/513) for an invalid login.
            if (resp.code in AUTH_ERROR_CODES) throw AuthException(S.wrongCredentials)
            if (!resp.isSuccessful) throw IOException(S.serverError(resp.code))
            resp.body?.string() ?: ""
        }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    companion object {
        /** Enter "demo" as the server to view the app with fake data. */
        const val DEMO_SERVER = "http://demo"

        private val AUTH_ERROR_CODES = setOf(401, 403, 511, 512, 513)
        private const val INDEX_VERSION = 4

        private val LIST_ACTIONS = listOf(
            "get_live_categories", "get_vod_categories", "get_series_categories",
            "get_live_streams", "get_vod_streams", "get_series",
        )

        /** Turns what the user types into a clean base URL. */
        fun normalizeServer(input: String): String {
            var s = input.trim()
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            s = s.substringBefore("/player_api.php").substringBefore("/get.php").substringBefore("?")
            return s.trimEnd('/')
        }
    }
}

private fun JsonReader.looseString(): String? = when (peek()) {
    JsonToken.NULL -> { nextNull(); null }
    JsonToken.STRING, JsonToken.NUMBER -> nextString()
    JsonToken.BOOLEAN -> nextBoolean().toString()
    else -> { skipValue(); null }
}

private fun JSONObject.text(vararg keys: String): String? {
    for (k in keys) {
        val v = opt(k)
        if (v is String && v.isNotBlank() && v != "null") return v.trim()
        if (v is Number) return v.toString()
    }
    return null
}

private fun JSONObject.backdrop(): String? = when (val b = opt("backdrop_path")) {
    is JSONArray -> (0 until b.length()).map { b.optString(it) }.firstOrNull { it.startsWith("http") }
    is String -> b.takeIf { it.startsWith("http") }
    else -> null
}

/** backdrop_path is a list (or sometimes a single string); take the first URL. */
private fun JsonReader.firstUrl(): String? = when (peek()) {
    JsonToken.BEGIN_ARRAY -> {
        beginArray()
        var first: String? = null
        while (hasNext()) {
            val v = looseString()
            if (first == null && v != null && v.startsWith("http")) first = v
        }
        endArray()
        first
    }
    else -> looseString()?.takeIf { it.startsWith("http") }
}
