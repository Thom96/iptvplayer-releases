package nl.iptvplayer.app.player

import android.app.Activity
import android.view.WindowManager
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import java.util.UUID

/**
 * Laat de tv weten dat er iets speelt, zolang de speler echt speelt:
 * - het scherm blijft aan (geen screensaver), en mag weer uit bij pauze, een fout of stilstand;
 * - een mediasessie meldt het afspelen aan Google TV, zodat ook de Assistent en mediaknoppen werken.
 *
 * Roep [release] aan voordat de speler zelf wordt vrijgegeven.
 */
class PlaybackSession(private val activity: Activity, private val player: Player) {

    private val session = MediaSession.Builder(activity, player)
        // Uniek per speler: het voorbeeld in Live en de spelerpagina kunnen tegelijk bestaan.
        .setId(UUID.randomUUID().toString())
        .build()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = keepScreenOn(isPlaying)
    }

    init {
        player.addListener(listener)
        keepScreenOn(player.isPlaying)
    }

    private fun keepScreenOn(on: Boolean) {
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (on) activity.window.addFlags(flag) else activity.window.clearFlags(flag)
    }

    fun release() {
        player.removeListener(listener)
        keepScreenOn(false)
        session.release()
    }
}
