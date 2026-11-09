package nl.iptvplayer.app.ui

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.lifecycle.ViewModelProvider
import nl.iptvplayer.app.AppState
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Scrolling through the channel list with the remote: the focused channel stays at the same height, holding an
 * arrow key moves at a calm pace, and each category remembers the channel you were on.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ChannelScrollingTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** Top edge of the focused element, in dp (the test screen has density 2). */
    private fun focusedTopDp(): Float = rule.onNode(isFocused()).fetchSemanticsNode().boundsInRoot.top / 2f

    private fun focusedText(): String =
        rule.onNode(isFocused()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString(" ").orEmpty()

    /** Number in front of the focused channel. */
    private fun focusedChannel(): Int = focusedText().trim().substringBefore(' ').toInt()

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.mainClock.advanceTimeBy(1_000)
    }

    /** Opens Live TV with the focus in the channel list; returns the focused channel number. */
    private fun openLiveChannels(): Int {
        org.robolectric.Shadows.shadowOf(rule.activity.packageManager)
            .setSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK, true)
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        rule.runOnUiThread { vm.setAppLanguage(nl.iptvplayer.app.i18n.AppLanguage.NL) }
        rule.runOnUiThread { vm.login("demo", "demo", "demo") }
        rule.waitUntil(20_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            vm.state == AppState.Ready
        }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        press(Key.DirectionRight)
        return focusedChannel()
    }

    private fun leaveLive() {
        press(Key.DirectionLeft)
        press(Key.DirectionLeft)
        press(Key.DirectionUp)
        rule.mainClock.advanceTimeBy(2_000)
    }

    @Test
    fun focusedChannelStaysAtTheSameHeight() {
        openLiveChannels()
        repeat(3) { press(Key.DirectionUp) } // to the top of the list
        val tops = (1..7).map {
            press(Key.DirectionDown)
            focusedTopDp()
        }
        println("Focus height per step (dp): $tops")
        // After the first steps the list slides under the focus instead of the focus moving down.
        val settled = tops.drop(4)
        assertTrue("focus stays put: $tops", settled.max() - settled.min() < 4f)
        assertTrue("focus sits in the upper half: $tops", settled.first() in 120f..240f)
        leaveLive()
    }

    @Test
    fun holdingDownMovesAtACalmPace() {
        val start = openLiveChannels()
        // A remote repeats a held key every 50 ms: 1 press + 12 repeats = 0.65 s held.
        val down = SystemClock.uptimeMillis()
        rule.runOnUiThread { rule.activity.dispatchKeyEvent(KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0)) }
        rule.mainClock.advanceTimeBy(400)
        for (i in 1..12) {
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(50))
            val t = SystemClock.uptimeMillis()
            rule.runOnUiThread { rule.activity.dispatchKeyEvent(KeyEvent(down, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, i)) }
            rule.mainClock.advanceTimeBy(50)
        }
        val t = SystemClock.uptimeMillis()
        rule.runOnUiThread { rule.activity.dispatchKeyEvent(KeyEvent(down, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN, 0)) }
        rule.mainClock.advanceTimeBy(1_000)
        val moved = focusedChannel() - start
        println("Held for 0.65 s: moved $moved channels")
        // Without calming every repeat would move (13 steps); at ~9 per second it is about 6.
        assertTrue("calm pace while held: moved $moved", moved in 4..8)
        leaveLive()
    }

    @Test
    fun eachCategoryRemembersItsChannel() {
        val start = openLiveChannels()
        repeat(2) { press(Key.DirectionDown) }
        val chosen = focusedChannel()
        assertEquals(start + 2, chosen)
        press(Key.DirectionLeft) // categories: "All channels"
        press(Key.DirectionDown) // next category
        press(Key.DirectionRight)
        val other = focusedChannel()
        press(Key.DirectionLeft)
        press(Key.DirectionUp) // back to "All channels"
        press(Key.DirectionRight)
        assertEquals("back on the channel you were on (other category showed $other)", chosen, focusedChannel())
        leaveLive()
    }
}
