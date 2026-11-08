package nl.iptvplayer.app.player

import android.app.Activity
import android.view.WindowManager
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import java.util.UUID

/**
 * Tells the TV that something is playing, as long as the player really plays:
 * - the screen stays on (no screensaver), and may turn off again on pause, an error or a stall;
 * - a media session reports playback to Google TV, so the Assistant and media keys work too.
 *
 * Call [release] before the player itself is released.
 */
class PlaybackSession(private val activity: Activity, private val player: Player) {

    private val session = MediaSession.Builder(activity, player)
        // Unique per player: the preview in Live and the player page can exist at the same time.
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
