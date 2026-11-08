package nl.iptvplayer.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {
    private var nextId = 1
    private fun movie(name: String, added: Long = 0) =
        MediaItem(Kind.MOVIE, nextId++, name, null, "1", added, 0.0, "mkv", TextMatch.yearFrom(name))

    private fun library(vararg movies: MediaItem) =
        Library(emptyList(), emptyList(), emptyList(), emptyList(), movies.toList(), emptyList(), 0)

    private fun search(lib: Library, q: String) = SearchEngine.search(lib, q).movies.map { it.name }

    @Test
    fun `exact title beats longer titles`() {
        val lib = library(
            movie("NL - The Matrix Reloaded (2003)"),
            movie("NL - The Matrix (1999)"),
            movie("EN - The Matrix Resurrections"),
        )
        assertEquals("NL - The Matrix (1999)", search(lib, "the matrix").first())
    }

    @Test
    fun `word parts in any order`() {
        val lib = library(movie("Breaking Bad"), movie("Bad Boys"), movie("Breaking Dawn"))
        assertEquals(listOf("Breaking Bad"), search(lib, "bad break"))
    }

    @Test
    fun `accents and capitals are ignored`() {
        val lib = library(movie("Amélie"), movie("Pokémon: De Film"))
        assertEquals(listOf("Amélie"), search(lib, "AMELIE"))
        assertEquals(listOf("Pokémon: De Film"), search(lib, "pokemon"))
    }

    @Test
    fun `joined word finds hyphenated title`() {
        val lib = library(movie("Spider-Man: No Way Home"), movie("Iron Man"))
        assertEquals(listOf("Spider-Man: No Way Home"), search(lib, "spiderman"))
    }

    @Test
    fun `a typo still gives a result`() {
        val lib = library(movie("Interstellar"), movie("Inception"))
        assertEquals(listOf("Interstellar"), search(lib, "intersteller"))
    }

    @Test
    fun `search by year`() {
        val lib = library(movie("Dune (2021)"), movie("Dune (1984)"))
        assertEquals(listOf("Dune (1984)"), search(lib, "dune 1984"))
    }

    @Test
    fun `labels before the title`() {
        assertEquals("The Matrix", TextMatch.cleanTitle("NL - The Matrix (1999)"))
        assertEquals("Dune", TextMatch.cleanTitle("|EN| Dune"))
        assertEquals("Dune", TextMatch.cleanTitle("[4K] Dune"))
    }

    @Test
    fun `display titles`() {
        assertEquals("The Matrix", TextMatch.displayTitle("NL - The Matrix (1999)"))
        assertEquals("NPO 1", TextMatch.displayTitle("NL | NPO 1"))
        assertEquals("Dune", TextMatch.displayTitle("|EN| Dune [4K]"))
        assertEquals("CSI: Miami", TextMatch.displayTitle("CSI: Miami"))
        assertEquals("300 - Rise of an Empire", TextMatch.displayTitle("300 - Rise of an Empire (2014)"))
        assertEquals("Spider-Man: No Way Home", TextMatch.displayTitle("EN - Spider-Man: No Way Home (2021)"))
        // The provider's notation: ┃ as separator and a bare year.
        assertEquals("Wealthy Soul", TextMatch.displayTitle("┃MULTI┃ Wealthy Soul 2024"))
        assertEquals("Lonely", TextMatch.displayTitle("┃MULTI┃ Lonely - 2025"))
        assertEquals("Tiny Fugitives", TextMatch.displayTitle("┃AR┃ Tiny Fugitives"))
        assertEquals("Blade Runner 2049", TextMatch.displayTitle("┃NL┃ Blade Runner 2049"))
        assertEquals(2024, TextMatch.yearFrom("┃MULTI┃ Wealthy Soul 2024"))
        assertEquals("Het Feest", TextMatch.episodeTitle("[NL] Gooische Vrouwen (2005) - S01E01 - Het Feest"))
        assertEquals("De ven zijn dood", TextMatch.episodeTitle("[NL] Gooische Vrouwen (2005) - S01E02 - De ven zijn dood"))
        assertEquals("Aflevering 3", TextMatch.episodeTitle("Aflevering 3"))
        assertEquals("The Medieval Tower", TextMatch.episodeTitle("Inside the Tower of London - S01E01.The.Medieval.Tower"))
        assertEquals("Mr. Robot", TextMatch.episodeTitle("Show - S01E01 - Mr. Robot"))
        assertEquals("Mr.Robot", TextMatch.episodeTitle("Show - S01E01 - Mr.Robot"))
        assertEquals("U.S.A.", TextMatch.episodeTitle("Show - S01E02 - U.S.A."))
        assertEquals("Dr.House.Pilot", TextMatch.episodeTitle("Dr.House.Pilot"))
        assertEquals("NPO 1 HD", TextMatch.displayTitle("┃NL┃ NPO 1 HD  ⏺ʳᵉᶜ"))
        assertEquals("NL", TextMatch.languageTag("┃NL┃ NETFLIX"))
        assertEquals("MULTI", TextMatch.languageTag("┃MULTI / LINGO┃ KIDS"))
    }

    @Test
    fun `one edit apart`() {
        assertTrue(TextMatch.withinOneEdit("matrix", "matrx"))
        assertTrue(TextMatch.withinOneEdit("matrix", "matrox"))
        assertFalse(TextMatch.withinOneEdit("matrix", "mtrx"))
    }

    @Test
    fun `contains ignoring spaces`() {
        assertTrue(TextMatch.containsIgnoringSpaces("spider man no way home", "spiderman"))
        assertTrue(TextMatch.containsIgnoringSpaces("the spider man", "spiderman"))
        assertTrue(TextMatch.containsIgnoringSpaces("x men", "xmen"))
        assertTrue(TextMatch.containsIgnoringSpaces("sspider man", "spiderman"))
        assertFalse(TextMatch.containsIgnoringSpaces("spider woman", "spiderman"))
        assertFalse(TextMatch.containsIgnoringSpaces("spider", "spiderman"))
    }

    @Test
    fun `clean up server URL`() {
        assertEquals("http://provider.tv:8080", XtreamClient.normalizeServer(" provider.tv:8080/ "))
        assertEquals("http://provider.tv:8080", XtreamClient.normalizeServer("http://provider.tv:8080/player_api.php?username=a"))
        assertEquals("https://x.y", XtreamClient.normalizeServer("https://x.y/get.php?username=a&password=b"))
    }
}
