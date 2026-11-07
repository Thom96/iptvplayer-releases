package nl.iptvplayer.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** De regels voor "verder kijken" en "gezien". */
@RunWith(RobolectricTestRunner::class)
class StorageTest {
    private val storage = Storage(RuntimeEnvironment.getApplication())

    private fun p(key: String, pos: Long, dur: Long = 100 * 60_000L, series: Int? = null) =
        Progress(key, "T", null, null, null, series, pos, dur, System.currentTimeMillis())

    @Test
    fun underThirtySecondsIsNotSaved() {
        storage.saveProgress(p("MOVIE:1", 20_000))
        assertNull(storage.progressFor("MOVIE:1"))
    }

    @Test
    fun ninetyThreePercentCountsAsWatched() {
        storage.saveProgress(p("MOVIE:1", 50 * 60_000L))
        assertEquals(50 * 60_000L, storage.progressFor("MOVIE:1")!!.positionMs)
        storage.saveProgress(p("MOVIE:1", 93 * 60_000L))
        assertNull(storage.progressFor("MOVIE:1"))
        assertTrue("MOVIE:1" in storage.watched.value)
    }

    @Test
    fun seriesHasOneRowWithTheLatestEpisode() {
        storage.saveProgress(p("EPISODE:1", 10 * 60_000L, series = 7))
        storage.saveProgress(p("EPISODE:2", 10 * 60_000L, series = 7))
        assertEquals(listOf("EPISODE:2"), storage.progress.value.map { it.key })
    }

    @Test
    fun nextUpReplacesFinishedEpisodeAndSurvivesRestart() {
        storage.saveProgress(p("EPISODE:1", 10 * 60_000L, series = 7))
        storage.markFinished("EPISODE:1")
        storage.saveNextUp(p("EPISODE:2", 0, 0, series = 7))
        val reopened = Storage(RuntimeEnvironment.getApplication())
        assertEquals(listOf("EPISODE:2"), reopened.progress.value.map { it.key })
        assertEquals(7, reopened.progress.value.single().seriesId)
        assertTrue("EPISODE:1" in reopened.watched.value)
    }

    @Test
    fun recentChannelsNewestFirstWithoutDuplicates() {
        listOf("LIVE:1", "LIVE:2", "LIVE:1").forEach(storage::addRecentLive)
        assertEquals(listOf("LIVE:1", "LIVE:2"), storage.recentLive.value)
    }

    @Test
    fun logoutKeepsServerAndUserForTheLoginScreen() {
        storage.credentials = Credentials("http://s", "jan", "geheim")
        storage.credentials = null
        assertNull(storage.credentials)
        assertEquals("http://s", storage.lastServer)
        assertEquals("jan", storage.lastUsername)
    }
}
