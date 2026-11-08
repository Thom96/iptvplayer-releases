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
 * Live TV met de afstandsbediening: de categorieën klappen in zodra je in de zenderlijst bent, links klapt ze
 * weer uit, en nog een keer links gaat naar de zijbalk zonder van tabblad te wisselen.
 * Met -Pframes=/pad worden schermafbeeldingen bewaard.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class LiveTabTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val frameDir = System.getProperty("scrollFrames")?.takeIf { it.isNotBlank() }?.let(::File)

    /** Linkerrand van het element met focus, in dp (het testscherm heeft dichtheid 2). */
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
        // Zijbalk: van Home omlaag naar Live, dan naar rechts: de zenderlijst, met de categorieën ingeklapt.
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        rule.mainClock.advanceTimeBy(2_000) // voorbeeld start
        val channels = focusedLeftDp()
        assertTrue("zenderlijst direct naast de ingeklapte categorieën: $channels", channels in 150f..220f)
        shot("live-zenders")

        press(Key.DirectionLeft)
        val categories = focusedLeftDp()
        assertTrue("links: categorieën klappen uit, naast de zijbalk: $categories", categories in 100f..140f)
        shot("live-categorieen")

        press(Key.DirectionRight)
        assertTrue("rechts: weer de zenderlijst", focusedLeftDp() in 150f..220f)
        press(Key.DirectionLeft)

        press(Key.DirectionLeft)
        assertTrue("nog een keer links: in de zijbalk", focusedLeftDp() < 76f)
        // Nog steeds op het Live-tabblad: de categorie "Alle zenders" staat in beeld.
        assertTrue(
            "nog op het Live-tabblad",
            rule.onAllNodes(androidx.compose.ui.test.hasText(S.allChannels), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )

        // Netjes weg van Live (omhoog naar Home), zodat de voorbeeldspeler stopt en volgende tests er geen last van hebben.
        press(Key.DirectionUp)
        rule.mainClock.advanceTimeBy(2_000)
    }
}
