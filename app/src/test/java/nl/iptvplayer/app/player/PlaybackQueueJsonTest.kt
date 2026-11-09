package nl.iptvplayer.app.player

import nl.iptvplayer.app.data.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackQueueJsonTest {
    @Test
    fun queueSurvivesSaveAndRestore() {
        val q = PlaybackQueue(
            items = listOf(
                PlayItem("EPISODE:1", Kind.EPISODE, "http://s/series/u/p/1.mkv", "Serie", "S1 E1 · Begin", "http://img", "mkv", 7),
                PlayItem("EPISODE:2", Kind.EPISODE, "http://s/series/u/p/2.mkv", "Serie", null, null, null, 7),
            ),
            startIndex = 1,
            startPositionMs = 123_456,
            intro = SeriesIntro("SERIES:7", "Serie", "2024 • Drama", null, "Iemand", "Verder kijken: S1 E2"),
        )
        val back = playbackQueueFromJson(q.toJson())!!
        assertEquals(1, back.startIndex)
        assertEquals(123_456L, back.startPositionMs)
        assertEquals(q.items.map { listOf(it.key, it.kind, it.url, it.title, it.subtitle, it.image, it.ext, it.seriesId, it.fallbackUrl) },
            back.items.map { listOf(it.key, it.kind, it.url, it.title, it.subtitle, it.image, it.ext, it.seriesId, it.fallbackUrl) })
        val intro = back.intro!!
        assertEquals(listOf("SERIES:7", "Serie", "2024 • Drama", null, "Iemand", "Verder kijken: S1 E2"),
            listOf(intro.seriesKey, intro.title, intro.meta, intro.plot, intro.cast, intro.playLabel))
    }

    @Test
    fun liveQueueKeepsFallbackUrl() {
        val q = PlaybackQueue(listOf(PlayItem("LIVE:3", Kind.LIVE, "http://a.ts", "NPO 1", fallbackUrl = "http://a.m3u8")))
        val back = playbackQueueFromJson(q.toJson())!!
        assertEquals("http://a.m3u8", back.items.single().fallbackUrl)
        assertEquals(true, back.isLive)
    }

    @Test
    fun episodeKeepsIntroDbLookup() {
        val q = PlaybackQueue(listOf(PlayItem("EPISODE:9", Kind.EPISODE, "http://e.mkv", "Serie", tmdbId = 1399, season = 2, episode = 5)))
        val back = playbackQueueFromJson(q.toJson())!!.items.single()
        assertEquals(listOf(1399, 2, 5), listOf(back.tmdbId, back.season, back.episode))
    }

    @Test
    fun brokenFileGivesNull() {
        assertNull(playbackQueueFromJson("{kapot"))
        assertNull(playbackQueueFromJson("""{"startIndex":0,"startPositionMs":0,"items":[]}"""))
    }
}
