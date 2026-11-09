package nl.iptvplayer.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** TMDB details from the Gallo server, merged over the provider's details. */
@RunWith(RobolectricTestRunner::class)
class MetadataTest {
    @Test
    fun notFoundIsNull() {
        assertNull(MetadataClient.parse("""{"found":false}"""))
    }

    @Test
    fun tmdbWinsAndProviderFillsTheGaps() {
        val meta = MetadataClient.parse(
            """{"found":true,"tmdbId":603,"title":"The Matrix","overview":"Neo ontdekt de waarheid.","poster":"https://image.tmdb.org/t/p/w500/p.jpg",
               "backdrop":null,"rating":8.2,"votes":25000,"year":1999,"genres":["Actie","Sciencefiction"]}""",
        )
        val provider = MovieInfo("English plot", null, "Keanu Reeves", null, "1999-03-31", 8160, 7.0, "https://prov/bd.jpg", "https://prov/p.jpg", "mkv")
        val merged = provider.withTmdb(meta)
        assertEquals("Neo ontdekt de waarheid.", merged.plot)
        assertEquals("Actie, Sciencefiction", merged.genre)
        assertEquals(8.2, merged.rating!!, 0.001)
        assertEquals("https://prov/bd.jpg", merged.backdrop) // TMDB had none
        assertEquals("https://image.tmdb.org/t/p/w500/p.jpg", merged.poster)
        assertEquals("mkv", merged.ext)
        assertEquals(provider, provider.withTmdb(null))
    }
}
