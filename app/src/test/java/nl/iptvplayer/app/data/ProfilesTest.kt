package nl.iptvplayer.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Profiles: the update keeps existing data, profiles don't share lists, the PIN and the kids filter. */
@RunWith(RobolectricTestRunner::class)
class ProfilesTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun existingDataBecomesTheFirstProfile() {
        // As before the update: favourites and progress saved without profiles.
        Storage(context).apply {
            toggleFavorite("MOVIE:1")
            addRecentLive("LIVE:5")
        }
        val profiles = ProfileStore(context)
        assertEquals(listOf(Profile.MAIN), profiles.profiles.map { it.id })
        assertEquals(Profile.MAIN, profiles.activeId)
        val storage = Storage(context).also { it.useProfile(profiles.activeId) }
        assertEquals(setOf("MOVIE:1"), storage.favorites.value)
        assertEquals(listOf("LIVE:5"), storage.recentLive.value)
    }

    @Test
    fun profilesKeepTheirOwnLists() {
        val storage = Storage(context)
        storage.toggleFavorite("MOVIE:1")
        storage.useProfile("kid")
        assertTrue(storage.favorites.value.isEmpty())
        storage.toggleFavorite("SERIES:2")
        storage.addRecentLive("LIVE:9")
        storage.useProfile(Profile.MAIN)
        assertEquals(setOf("MOVIE:1"), storage.favorites.value)
        assertTrue(storage.recentLive.value.isEmpty())
        storage.useProfile("kid")
        assertEquals(setOf("SERIES:2"), storage.favorites.value)
        storage.deleteProfileData("kid")
        storage.useProfile("kid")
        assertTrue(storage.favorites.value.isEmpty())
    }

    @Test
    fun profilesSurviveRestartAndMainCannotBeDeleted() {
        val store = ProfileStore(context)
        store.save(Profile("p1", "Lotte", 2, kid = true, hidden = setOf("LIVE:3")))
        store.activeId = "p1"
        store.delete(Profile.MAIN)
        val reopened = ProfileStore(context)
        assertEquals(listOf(Profile.MAIN, "p1"), reopened.profiles.map { it.id })
        assertEquals("p1", reopened.activeId)
        assertEquals(setOf("LIVE:3"), reopened.get("p1")!!.hidden)
        reopened.delete("p1")
        assertEquals(Profile.MAIN, reopened.activeId)
    }

    @Test
    fun atMostFiveProfiles() {
        val store = ProfileStore(context)
        repeat(7) { store.save(Profile("p$it", "P$it", 0)) }
        assertEquals(Profile.MAX, store.profiles.size)
    }

    @Test
    fun pin() {
        val store = ProfileStore(context)
        assertFalse(store.hasPin)
        store.setPin("1234")
        assertTrue(ProfileStore(context).checkPin("1234"))
        assertFalse(store.checkPin("4321"))
        store.setPin(null)
        assertFalse(store.hasPin)
        assertFalse(store.checkPin("1234"))
    }

    @Test
    fun adultCategoriesLookAdult() {
        listOf("XXX", "NL | ADULTS", "18+ Movies", "+18 live", "Porn HD", "EROTIC", "For Adults Only").forEach {
            assertTrue(it, ParentalFilter.looksAdult(it))
        }
        listOf("Kids", "NL | Films 2018", "Adultos? no", "Sports 1", "Netflix Series").forEach {
            assertFalse(it, ParentalFilter.looksAdult(it))
        }
    }

    @Test
    fun kidProfileOnlySeesAllowedCategories() {
        fun item(kind: Kind, id: Int, cat: String, name: String = "Item $id") = MediaItem(kind, id, name, null, cat, 0, 0.0, null, null)
        val lib = Library(
            liveCategories = listOf(Category("1", "NL Algemeen"), Category("2", "XXX")),
            movieCategories = listOf(Category("3", "Kids"), Category("4", "Horror")),
            seriesCategories = emptyList(),
            live = listOf(item(Kind.LIVE, 1, "1"), item(Kind.LIVE, 2, "2")),
            movies = listOf(item(Kind.MOVIE, 3, "3"), item(Kind.MOVIE, 4, "4"), item(Kind.MOVIE, 5, "3", "XXX Night")),
            series = emptyList(),
            updatedAt = 0,
        )
        val grownUp = Profile(Profile.MAIN, "", 0)
        assertEquals(lib, ParentalFilter.apply(lib, grownUp))

        val kid = Profile("k", "Lotte", 0, kid = true, hidden = setOf("MOVIE:4"))
        val seen = ParentalFilter.apply(lib, kid)
        assertEquals(listOf("1"), seen.liveCategories.map { it.id })
        assertEquals(listOf(1), seen.live.map { it.id })
        assertEquals(listOf(3), seen.movies.map { it.id })

        // The parent explicitly allowed the adult-looking category.
        val allowed = ParentalFilter.apply(lib, kid.copy(shown = setOf("LIVE:2")))
        assertEquals(listOf(1, 2), allowed.live.map { it.id })
    }
}
