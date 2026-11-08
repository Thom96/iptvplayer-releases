package nl.iptvplayer.app.player

import android.app.Activity
import android.os.Looper
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The screen only stays on (no screensaver) while something is really playing. */
@RunWith(RobolectricTestRunner::class)
class PlaybackSessionTest {

    @OptIn(UnstableApi::class)
    private class FakePlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        private var playWhenReady = false
        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().add(Player.COMMAND_PLAY_PAUSE).build())
            .setPlaylist(listOf(MediaItemData.Builder("zender").build()))
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            this.playWhenReady = playWhenReady
            return Futures.immediateVoidFuture()
        }
    }

    private fun Activity.keepsScreenOn() =
        window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

    @Test
    fun screenStaysOnOnlyWhilePlaying() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val player = FakePlayer()
        val session = PlaybackSession(activity, player)
        assertFalse(activity.keepsScreenOn())

        player.play()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.keepsScreenOn())

        player.pause()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(activity.keepsScreenOn())

        player.play()
        shadowOf(Looper.getMainLooper()).idle()
        session.release()
        assertFalse(activity.keepsScreenOn())
    }
}
