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

    @Test
    fun categoriesCollapseAndLeftReachesTheMenu() {
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
        // Side rail: down from Home to Live, then right: the channel list, with the categories collapsed.
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        rule.mainClock.advanceTimeBy(2_000) // preview starts
        val channels = focusedLeftDp()
        assertTrue("channel list right next to the collapsed categories: $channels", channels in 150f..220f)
        shot("live-channels")

        press(Key.DirectionLeft)
        val categories = focusedLeftDp()
        assertTrue("left: categories expand, next to the side rail: $categories", categories in 100f..140f)
        shot("live-categories")

        press(Key.DirectionRight)
        assertTrue("right: back in the channel list", focusedLeftDp() in 150f..220f)
        press(Key.DirectionLeft)

        press(Key.DirectionLeft)
        assertTrue("left once more: in the side rail", focusedLeftDp() < 76f)
        // Still on the Live tab: the "All channels" category is on screen.
        assertTrue(
            "still on the Live tab",
            rule.onAllNodes(androidx.compose.ui.test.hasText(S.allChannels), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )

        // Leave Live cleanly (up to Home), so the preview player stops and doesn't affect later tests.
        press(Key.DirectionUp)
        rule.mainClock.advanceTimeBy(2_000)
    }
}
