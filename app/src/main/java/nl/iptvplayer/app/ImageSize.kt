package nl.iptvplayer.app

import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.size.Dimension

/**
 * Fetches TMDB images in the size they are shown at instead of the large size the provider links to.
 * A poster card is about 250 px wide; a 500 px or original image costs data, memory and decoding time
 * while scrolling.
 */
object ImageSize {
    /** Widths TMDB serves for every image; the smallest one at least as wide as the card is used. */
    private val WIDTHS = intArrayOf(185, 342, 500, 780)
    private val TMDB = Regex("""^(https?://image\.tmdb\.org/t/p/)([^/]+)(/.+)$""")
    private val SIZE_WIDTH = Regex("""^w(\d+)""")

    /** The URL of the same TMDB image at least [px] wide, or null to keep [url] as it is. */
    fun tmdbForWidth(url: String, px: Int): String? {
        if (px <= 0) return null
        val match = TMDB.matchEntire(url) ?: return null
        val (prefix, size, path) = match.destructured
        val wanted = WIDTHS.firstOrNull { it >= px } ?: return null
        val current = if (size == "original") Int.MAX_VALUE else SIZE_WIDTH.find(size)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        if (wanted >= current) return null // never ask for a larger image than the provider gave
        return "${prefix}w$wanted$path"
    }
}

/** Coil interceptor that applies [ImageSize.tmdbForWidth] using the size the image is drawn at. */
class TmdbSizeInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data as? String ?: return chain.proceed(chain.request)
        val width = (chain.size.width as? Dimension.Pixels)?.px ?: return chain.proceed(chain.request)
        val smaller = ImageSize.tmdbForWidth(url, width) ?: return chain.proceed(chain.request)
        return chain.proceed(chain.request.newBuilder().data(smaller).build())
    }
}
