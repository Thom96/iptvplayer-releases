package nl.iptvplayer.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The rules for "continue watching" and "watched". */
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

    @Test
    fun learnedIntroIsKeptPerSeries() {
        assertNull(storage.learnedIntro(7))
        storage.saveLearnedIntro(7, 12_000, 95_000)
        storage.saveLearnedIntro(8, 0, 60_000)
        storage.saveLearnedIntro(7, 15_000, 90_000)
        assertEquals(15_000L to 90_000L, storage.learnedIntro(7))
        assertEquals(0L to 60_000L, storage.learnedIntro(8))
    }
}

/** The password is never stored in readable form, not even after converting an old installation. */
@RunWith(RobolectricTestRunner::class)
class StoragePasswordTest {
    private val app = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences("iptv", android.content.Context.MODE_PRIVATE)

    /** Fake encryption (the Android Keystore doesn't exist in Robolectric). */
    private class Reverse : SecretCipher {
        var broken = false
        override fun encrypt(plain: String) = "enc:" + plain.reversed()
        override fun decrypt(encrypted: String) = if (broken) null else encrypted.removePrefix("enc:").reversed()
    }

    @Test
    fun passwordIsStoredEncrypted() {
        val cipher = Reverse()
        Storage(app, cipher).credentials = Credentials("http://s", "jan", "geheim")
        assertNull(prefs.getString("password", null))
        assertEquals("enc:mieheg", prefs.getString("passwordEnc", null))
        assertEquals("geheim", Storage(app, cipher).credentials!!.password)
    }

    @Test
    fun oldPlainPasswordIsMigrated() {
        prefs.edit().putString("server", "http://s").putString("username", "jan").putString("password", "geheim").commit()
        val creds = Storage(app, Reverse()).credentials
        assertEquals("geheim", creds!!.password)
        assertNull(prefs.getString("password", null))
        assertEquals("enc:mieheg", prefs.getString("passwordEnc", null))
    }

    @Test
    fun lostKeyMeansLoginAgainWithNamePrefilled() {
        val cipher = Reverse()
        val storage = Storage(app, cipher)
        storage.credentials = Credentials("http://s", "jan", "geheim")
        cipher.broken = true
        assertNull(storage.credentials)
        assertEquals("jan", storage.lastUsername)
    }

    @Test
    fun logoutRemovesPassword() {
        val storage = Storage(app, Reverse())
        storage.credentials = Credentials("http://s", "jan", "geheim")
        storage.credentials = null
        assertNull(prefs.getString("passwordEnc", null))
        assertNull(prefs.getString("password", null))
    }
}
