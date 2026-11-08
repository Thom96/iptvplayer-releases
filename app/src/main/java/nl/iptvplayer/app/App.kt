package nl.iptvplayer.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import nl.iptvplayer.app.data.Storage
import nl.iptvplayer.app.i18n.AppLanguage
import nl.iptvplayer.app.i18n.I18n
import nl.iptvplayer.app.player.PlaybackQueue
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class App : Application(), ImageLoaderFactory {

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .build()
    }

    val storage: Storage by lazy { Storage(this) }

    /** Queue for the player; passed through memory because channel lists are too big for an Intent. */
    var pendingQueue: PlaybackQueue? = null

    /** Series whose episode list should open after the player closes ("Episodes and more"). */
    var pendingSeriesDetail: String? = null

    override fun onCreate() {
        super.onCreate()
        I18n.language = AppLanguage.fromCode(storage.appLanguage) ?: AppLanguage.DEFAULT
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(http)
            .crossfade(false) // crossfading costs drawing work while scrolling
            .respectCacheHeaders(false)
            .build()

    companion object {
        // Many IPTV servers refuse unknown players; a VLC user agent is accepted almost everywhere.
        const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"
    }
}
