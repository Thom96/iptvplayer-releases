package nl.iptvplayer.app.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringsTest {
    @Test
    fun everyLanguageTranslatesAllGenres() {
        AppLanguage.entries.forEach { lang ->
            assertEquals("genres in ${lang.nativeName}", En.genres.keys, lang.strings.genres.keys)
        }
    }

    @Test
    fun languageCodesAreUnique() {
        assertEquals(AppLanguage.entries.size, AppLanguage.entries.map { it.code }.toSet().size)
        assertTrue(AppLanguage.DEFAULT == AppLanguage.EN)
    }
}
