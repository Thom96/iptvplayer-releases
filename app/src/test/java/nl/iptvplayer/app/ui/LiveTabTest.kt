package nl.iptvplayer.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
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
import nl.iptvplayer.app.i18n.S
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Live TV with the remote: the categories collapse once you're in the channel list, left expands them again,
 * and left once more goes to the side rail without switching tabs.
 * With -Pframes=/path, screenshots are saved.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class LiveTabTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val frameDir = System.getProperty("scrollFrames")?.takeIf { it.isNotBlank() }?.let(::File)

    /** Left edge of the focused element, in dp (the test screen has density 2). */
    private fun focusedLeftDp(): Float =
        rule.onNode(isFocused(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left / 2f

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun shot(name: String) {
        val dir = frameDir ?: return
        dir.mkdirs()
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(bmp))
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun focusedBounds() = rule.onNode(isFocused(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun openLive(): AppViewModel {
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
        // Side rail: down from Home to Live, then right into the tab.
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        press(Key.DirectionRight)
        return vm
    }

    private fun leave() {
        // Leave Live cleanly (Back until the side rail: in the guide the first Back returns to now; then up to
        // Home), so the preview player stops and doesn't keep later tests busy.
        repeat(3) {
            if (focusedLeftDp() >= 76f) {
                rule.onRoot().performKeyInput { pressKey(Key.Back) }
                rule.mainClock.advanceTimeBy(1_000)
            }
        }
        press(Key.DirectionUp)
        rule.mainClock.advanceTimeBy(2_000)
    }

    @Test
    fun categoriesStayAPlainListAndLeftReachesTheMenu() {
        openLive()
        // Into the channel list (from the categories or the tabs, whichever right landed on).
        while (focusedLeftDp() < 300f) press(if (focusedBounds().top / 2f < 50f) Key.DirectionDown else Key.DirectionRight)
        press(Key.DirectionDown)
        rule.mainClock.advanceTimeBy(2_000) // preview starts
        val channels = focusedLeftDp()
        assertTrue("channel list next to the categories: $channels", channels in 300f..360f)
        shot("live-channels")

        press(Key.DirectionLeft)
        val categories = focusedLeftDp()
        assertTrue("left: categories, next to the side rail: $categories", categories in 100f..130f)
        // The categories don't collapse: still the same width when the focus is back in the channels.
        press(Key.DirectionRight)
        assertTrue("right: back in the channel list", focusedLeftDp() in 300f..360f)
        assertTrue(
            "categories still visible",
            rule.onAllNodes(androidx.compose.ui.test.hasText(S.allChannels), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
        press(Key.DirectionLeft)
        press(Key.DirectionLeft)
        assertTrue("left once more: in the side rail", focusedLeftDp() < 76f)
        assertTrue(
            "still on the Live tab",
            rule.onAllNodes(androidx.compose.ui.test.hasText(S.allChannels), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
        press(Key.DirectionUp)
        rule.mainClock.advanceTimeBy(2_000)
    }

    @Test
    fun guideAtTheTopReplaysAPastProgramme() {
        val vm = openLive()
        // Up to "Channels / TV guide", right = TV guide.
        repeat(3) { if (focusedBounds().top / 2f > 50f) press(Key.DirectionUp) }
        press(Key.DirectionRight)
        assertTrue(
            "guide shown",
            rule.onAllNodes(androidx.compose.ui.test.hasText(S.guideWatchLive), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(androidx.compose.ui.test.hasText(S.loadingGuide, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
        press(Key.DirectionDown) // category chips: lands on the chosen one ("All channels")
        assertTrue(
            "still all channels",
            rule.onAllNodes(androidx.compose.ui.test.hasText("NPO 1"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
        press(Key.DirectionDown) // timeline
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            vm.guide.containsKey(300) // first demo channel
        }
        rule.waitForIdle()
        shot("live-guide")
        // Channel 1 of the demo has an archive: two programmes back and OK plays it again.
        press(Key.DirectionLeft)
        press(Key.DirectionLeft)
        shot("live-guide-past")
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        // The app the view model starts the player with (in a test run this can differ from rule.activity.application).
        val app = vm.getApplication<nl.iptvplayer.app.App>()
        // Only check that the player is asked for (it is not started in this test).
        rule.waitUntil(5_000) { app.pendingQueue != null }
        assertEquals(
            nl.iptvplayer.app.player.PlayerActivity::class.java.name,
            org.robolectric.Shadows.shadowOf(rule.activity).nextStartedActivity?.component?.className,
        )
        assertEquals(nl.iptvplayer.app.data.Kind.CATCHUP, app.pendingQueue?.items?.single()?.kind)
        assertTrue(vm.guide.isNotEmpty())
        leave()
    }
}
