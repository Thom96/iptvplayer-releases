package nl.iptvplayer.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import nl.iptvplayer.app.data.Storage
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

    /** Wachtrij voor de speler; via het geheugen doorgegeven omdat zenderlijsten te groot zijn voor een Intent. */
    var pendingQueue: PlaybackQueue? = null

    /** Serie waarvan de afleveringenlijst geopend moet worden na het sluiten van de speler ("Afleveringen en meer"). */
    var pendingSeriesDetail: String? = null

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(http)
            .crossfade(false) // overvloeien kost tekenwerk tijdens het scrollen
            .respectCacheHeaders(false)
            .components {
                // Bewegende GIF's (de haan op het laadscherm).
                if (android.os.Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .build()

    companion object {
        // Veel IPTV-servers weigeren onbekende spelers; een VLC-user-agent wordt vrijwel overal geaccepteerd.
        const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"
    }
}
