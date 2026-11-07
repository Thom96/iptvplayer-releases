package nl.iptvplayer.app.player

import nl.iptvplayer.app.i18n.AppLanguage
import nl.iptvplayer.app.i18n.I18n
import nl.iptvplayer.app.i18n.S
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import nl.iptvplayer.app.App
import nl.iptvplayer.app.R
import nl.iptvplayer.app.data.Kind
import nl.iptvplayer.app.data.Progress
import nl.iptvplayer.app.ui.AppTheme

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private lateinit var app: App
    private lateinit var queue: PlaybackQueue
    private lateinit var playerView: PlayerView
    /** De speler; Compose tekent de bediening zodra hij er is. */
    private var player by mutableStateOf<ExoPlayer?>(null)
    /** Scherm aan en mediasessie zolang er iets speelt. */
    private var session: PlaybackSession? = null

    /** Items waarvoor de fallback-URL al geprobeerd is. */
    private val usedFallback = HashSet<String>()
    /** Live: aantal herstartpogingen sinds het laatste beeld (netwerkhapering). */
    private var liveRetries = 0
    private var resumeIndex = 0
    private var resumePosition = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val saveTicker = object : Runnable {
        override fun run() {
            saveProgress()
            handler.postDelayed(this, 10_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as App
        queue = app.pendingQueue ?: run { finish(); return }
        resumeIndex = savedInstanceState?.getInt("index") ?: queue.startIndex
        resumePosition = savedInstanceState?.getLong("position")
            ?: if (queue.isLive) C.TIME_UNSET else queue.startPositionMs

        // Alleen het beeld en de ondertiteling; de bediening is eigen Compose-code (PlayerControls).
        playerView = PlayerView(this).apply {
            useController = false
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setShutterBackgroundColor(Color.BLACK)
            subtitleView?.let(::styleSubtitles)
        }

        setContent {
            AppTheme {
                val favorites by app.storage.favorites.collectAsState()
                AndroidView(factory = { playerView }, modifier = Modifier.fillMaxSize())
                player?.let { exo ->
                    PlayerControls(
                        player = exo,
                        queue = queue,
                        onExit = { finish() },
                        // Ondertiteling omhoog als de bediening in beeld is, zodat ze elkaar niet raken.
                        onControlsVisible = { visible -> playerView.subtitleView?.setBottomPaddingFraction(if (visible) 0.26f else 0.08f) },
                        onNextEpisode = { nextEpisode() },
                        onMoreEpisodes = {
                            app.pendingSeriesDetail = queue.intro?.seriesKey
                            finish()
                        },
                        isFavorite = queue.intro?.seriesKey?.let { it in favorites } == true,
                        onToggleFavorite = { queue.intro?.seriesKey?.let(app.storage::toggleFavorite) },
                    )
                }
            }
        }
    }

    /** Witte ondertiteling met schaduw, zonder zwarte balk, in het lettertype van de app. */
    private fun styleSubtitles(view: SubtitleView) {
        view.setApplyEmbeddedStyles(false)
        view.setApplyEmbeddedFontSizes(false)
        view.setStyle(
            CaptionStyleCompat(
                Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, Color.BLACK,
                ResourcesCompat.getFont(this, R.font.outfit_500),
            ),
        )
        view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.2f)
        view.setBottomPaddingFraction(0.08f)
    }

    override fun onStart() {
        super.onStart()
        if (::queue.isInitialized) initPlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("index", resumeIndex)
        outState.putLong("position", resumePosition)
    }

    private fun initPlayer() {
        val dataSource = OkHttpDataSource.Factory(app.http).setUserAgent(App.USER_AGENT)
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(fastStartLoadControl())
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
        // Ondertiteling in de taal van de app automatisch aan als die er is (niet bij Engels: meestal is
        // het geluid al Engels); audio in de originele taal.
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage(I18n.language.takeIf { it != AppLanguage.EN }?.code)
            .build()
        playerView.player = exo

        exo.setMediaItems(queue.items.map { toMediaItem(it, it.url) }, resumeIndex, resumePosition)
        exo.repeatMode = if (queue.isLive) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                liveRetries = 0
                // Automatisch door naar de volgende aflevering: de vorige is uitgekeken.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && !queue.isLive) {
                    finishItem(exo.currentMediaItemIndex - 1)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) liveRetries = 0
                if (state == Player.STATE_ENDED && !queue.isLive) {
                    saveProgress()
                    finish()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val item = queue.items.getOrNull(exo.currentMediaItemIndex) ?: return
                if (item.fallbackUrl != null && usedFallback.add(item.key)) {
                    exo.replaceMediaItem(exo.currentMediaItemIndex, toMediaItem(item, item.fallbackUrl))
                    exo.prepare()
                    exo.play()
                    return
                }
                // Live na een netwerkhapering: een paar keer opnieuw starten in plaats van stil te blijven staan.
                if (queue.isLive && liveRetries < LIVE_RETRIES) {
                    liveRetries++
                    handler.postDelayed({
                        if (player === exo) {
                            exo.prepare()
                            exo.play()
                        }
                    }, 2_000L * liveRetries)
                    return
                }
                Toast.makeText(
                    this@PlayerActivity,
                    S.playbackFailed(error.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')),
                    Toast.LENGTH_LONG,
                ).show()
            }
        })
        exo.playWhenReady = true
        exo.prepare()
        player = exo
        session = PlaybackSession(this, exo)
        handler.postDelayed(saveTicker, 10_000)
    }

    private fun releasePlayer() {
        handler.removeCallbacks(saveTicker)
        val exo = player ?: return
        saveProgress()
        // Pas bij het verlaten bijwerken: anders verschuift "Recent bekeken" tijdens het zappen.
        currentItem()?.takeIf { it.kind == Kind.LIVE }?.let { app.storage.addRecentLive(it.key) }
        resumeIndex = exo.currentMediaItemIndex
        resumePosition = if (queue.isLive) C.TIME_UNSET else exo.currentPosition.coerceAtLeast(0)
        player = null
        session?.release()
        session = null
        playerView.player = null
        exo.release()
    }

    private fun toMediaItem(item: PlayItem, url: String): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(url)
            .setMediaId(item.key)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setSubtitle(item.subtitle).build())
        if (url.endsWith(".m3u8")) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        return builder.build()
    }

    private fun currentItem(): PlayItem? = player?.let { queue.items.getOrNull(it.currentMediaItemIndex) }

    private fun saveProgress() {
        val exo = player ?: return
        val item = currentItem() ?: return
        if (item.kind != Kind.MOVIE && item.kind != Kind.EPISODE) return
        val duration = exo.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        val position = exo.currentPosition
        app.storage.saveProgress(
            Progress(
                key = item.key,
                title = item.title,
                subtitle = item.subtitle,
                image = item.image,
                ext = item.ext,
                seriesId = item.seriesId,
                positionMs = position,
                durationMs = duration,
                updatedAt = System.currentTimeMillis(),
            )
        )
        if (position >= duration * 0.93) queueNextUp(exo.currentMediaItemIndex)
    }

    /** Naar de volgende aflevering (ook in het volgende seizoen); de huidige telt als uitgekeken. */
    private fun nextEpisode() {
        val exo = player ?: return
        val index = exo.currentMediaItemIndex
        if (index >= queue.items.size - 1) return
        // Halverwege overslaan is niet "uitgekeken": dan alleen de positie bewaren.
        if (exo.duration > 0 && exo.currentPosition >= exo.duration * 0.93) finishItem(index) else saveProgress()
        exo.seekToNextMediaItem()
        exo.play()
    }

    private fun finishItem(index: Int) {
        val item = queue.items.getOrNull(index) ?: return
        app.storage.markFinished(item.key)
        queueNextUp(index)
    }

    /** Zet de volgende aflevering klaar in "Serie verder kijken". */
    private fun queueNextUp(index: Int) {
        if (queue.items.getOrNull(index)?.kind != Kind.EPISODE) return
        val next = queue.items.getOrNull(index + 1)?.takeIf { it.kind == Kind.EPISODE } ?: return
        if (app.storage.progressFor(next.key) != null) return
        app.storage.saveNextUp(
            Progress(next.key, next.title, next.subtitle, next.image, next.ext, next.seriesId, 0, 0, System.currentTimeMillis()),
        )
    }

    private companion object {
        const val LIVE_RETRIES = 3
    }
}
