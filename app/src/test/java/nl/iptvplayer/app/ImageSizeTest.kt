package nl.iptvplayer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageSizeTest {
    private val base = "https://image.tmdb.org/t/p/"

    @Test
    fun picksSmallestWidthThatFits() {
        assertEquals("${base}w342/abc.jpg", ImageSize.tmdbForWidth("${base}w500/abc.jpg", 256))
        assertEquals("${base}w185/abc.jpg", ImageSize.tmdbForWidth("${base}original/abc.jpg", 180))
        assertEquals("${base}w780/abc.jpg", ImageSize.tmdbForWidth("${base}original/abc.jpg", 682))
    }

    @Test
    fun understandsCombinedSizes() {
        assertEquals("${base}w342/abc.jpg", ImageSize.tmdbForWidth("${base}w600_and_h900_bestv2/abc.jpg", 300))
    }

    @Test
    fun neverAsksForALargerImage() {
        assertNull(ImageSize.tmdbForWidth("${base}w342/abc.jpg", 300))
        assertNull(ImageSize.tmdbForWidth("${base}w185/abc.jpg", 300))
        assertNull(ImageSize.tmdbForWidth("${base}w500/abc.jpg", 1200))
    }

    @Test
    fun leavesOtherUrlsAlone() {
        assertNull(ImageSize.tmdbForWidth("https://picsum.photos/seed/p1/300/450", 200))
        assertNull(ImageSize.tmdbForWidth("http://provider.example/logo.png", 200))
        assertNull(ImageSize.tmdbForWidth("${base}w500/abc.jpg", 0))
    }
}
