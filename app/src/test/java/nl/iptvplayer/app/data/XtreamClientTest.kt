package nl.iptvplayer.app.data

import kotlinx.coroutines.runBlocking
import nl.iptvplayer.app.i18n.S
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Reading lists from the server without network: the "downloaded" files are already in the cache. */
@RunWith(RobolectricTestRunner::class)
class XtreamClientTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val creds = Credentials("http://iptv.example:8080", "jan de vries", "p@ss/1")

    private fun client(): XtreamClient {
        val c = XtreamClient(creds, OkHttpClient(), tmp.root)
        // The cache directory has a name that depends on server and user; let the client create it and fill it.
        val dir = File(tmp.root, "lists-" + (creds.server + "|" + creds.username).hashCode().toUInt().toString(16))
        dir.mkdirs()
        fun put(action: String, json: String) = File(dir, "$action.json").writeText(json)
        put("get_live_categories", """[{"category_id":"1","category_name":" NL | Algemeen "}]""")
        put("get_vod_categories", """[{"category_id":"10","category_name":""},{"category_name":"zonder id"}]""")
        put("get_series_categories", """[]""")
        put("get_live_streams", """[
            {"num":1,"name":"NPO 1","stream_id":"11","stream_icon":"http://logo","category_id":"1","tv_archive":1,"tv_archive_duration":"7"},
            {"num":2,"name":"Zonder id"},
            "rommel"
        ]""")
        put("get_vod_streams", """[
            {"name":"Film (2019)","stream_id":21,"rating":"7.4","container_extension":"mkv","added":"1700000000","stream_icon":"geen-url"},
            {"title":"Title only","stream_id":22,"rating_5based":4,"plot":"  Plot  "}
        ]""")
        put("get_series", """[{"name":"Serie","series_id":"31","cover":"http://cover","backdrop_path":["","http://bg"],"last_modified":"1700000500","genre":"Drama"}]""")
        return c
    }

    @Test
    fun readsListsFromServerJson() = runBlocking {
        val lib = client().loadLibrary(forceDownload = false)
        assertEquals(listOf(Category("1", "NL | Algemeen")), lib.liveCategories)
        assertEquals("category without a name gets a translated name", listOf(Category("10", S.unnamedCategory)), lib.movieCategories)

        val npo = lib.live.single()
        assertEquals(listOf<Any?>(11, "NPO 1", "http://logo", "1", 1, 7), listOf(npo.id, npo.name, npo.image, npo.categoryId, npo.num, npo.archiveDays))

        val (movie, titleOnly) = lib.movies
        assertEquals(2019, movie.year)
        assertEquals(7.4, movie.rating, 0.0)
        assertEquals("mkv", movie.ext)
        assertNull("no http address = no image", movie.image)
        assertEquals("Title only", titleOnly.name)
        assertEquals("5-point scale becomes 10-point scale", 8.0, titleOnly.rating, 0.0)
        assertEquals("Plot", titleOnly.plot)

        val serie = lib.series.single()
        assertEquals(listOf<Any?>(31, "http://cover", "http://bg", 1700000500L, "Drama"), listOf(serie.id, serie.image, serie.backdrop, serie.added, serie.genre))
    }

    @Test
    fun secondStartReadsTheSameFromTheIndex() = runBlocking {
        val first = client().loadLibrary(forceDownload = false)
        val second = XtreamClient(creds, OkHttpClient(), tmp.root).loadLibrary(forceDownload = false)
        fun Library.dump() = listOf(liveCategories, movieCategories, seriesCategories) +
            listOf(live, movies, series).map { l -> l.map { listOf(it.kind, it.id, it.name, it.image, it.categoryId, it.added, it.rating, it.ext, it.year, it.num, it.archiveDays, it.plot, it.genre, it.backdrop) } }
        assertEquals(first.dump(), second.dump())
    }

    @Test
    fun streamUrlsEscapeLoginDetails() {
        val c = XtreamClient(creds, OkHttpClient(), tmp.root)
        assertEquals("http://iptv.example:8080/movie/jan%20de%20vries/p%40ss%2F1/21.mkv", c.movieUrl(21, "mkv"))
        assertEquals("http://iptv.example:8080/series/jan%20de%20vries/p%40ss%2F1/5.mp4", c.episodeUrl(5, null))
        assertEquals("http://iptv.example:8080/live/jan%20de%20vries/p%40ss%2F1/11.ts", c.liveUrl(11, "ts"))
    }

    @Test
    fun catchupUrlUsesServerTimeAndRoundsMinutesUp() {
        val c = XtreamClient(creds, OkHttpClient(), tmp.root)
        val entry = CatchupEntry("Journaal", null, start = 0, end = 30 * 60_000L + 1, serverStart = "2026-10-24 13:30:00", hasArchive = true)
        assertEquals("http://iptv.example:8080/timeshift/jan%20de%20vries/p%40ss%2F1/31/2026-10-24:13-30/11.ts", c.catchupUrl(11, entry))
    }

    @Test
    fun serverAddressIsCleanedUp() {
        assertEquals("http://iptv.example:8080", XtreamClient.normalizeServer(" iptv.example:8080/player_api.php?username=x "))
        assertEquals("https://a.b", XtreamClient.normalizeServer("https://a.b/"))
        assertEquals("http://a.b", XtreamClient.normalizeServer("a.b/get.php?type=m3u"))
    }
}
