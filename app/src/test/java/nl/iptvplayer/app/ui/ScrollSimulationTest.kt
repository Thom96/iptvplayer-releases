package nl.iptvplayer.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.math.abs

/**
 * Bootst het bladeren door Series en Films na met de afstandsbediening: de demobibliotheek, pijltjes
 * ingedrukt gehouden (zoals op de tv, ~20 herhalingen per seconde), en per frame waar de kaart met
 * focus staat. Haperen = de kaart springt heen en weer of staat lang niet stil.
 *
 * Draaien: ./gradlew testDebugUnitTest --tests '*ScrollSimulationTest*' -Pframes=/pad (frames optioneel).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ScrollSimulationTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val frameDir = System.getProperty("scrollFrames")?.takeIf { it.isNotBlank() }?.let(::File)
    private var frameNo = 0

    private fun focusedBounds(): Rect? =
        runCatching { rule.onNode(isFocused(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }.getOrNull()

    private fun frame(log: MutableList<Rect?>) {
        rule.mainClock.advanceTimeByFrame()
        log += focusedBounds()
        frameDir?.let { dir ->
            rule.runOnUiThread {
                val view = rule.activity.window.decorView
                val bmp = Bitmap.createBitmap(view.width / 2, view.height / 2, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                canvas.scale(0.5f, 0.5f)
                view.draw(canvas)
                File(dir, "f%04d.png".format(frameNo++)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    /** [presses] keer [key], elke [intervalMs] (50 = ingedrukt houden), daarna uitlopen. */
    private fun hold(key: Key, presses: Int, intervalMs: Long = 50): List<Rect?> {
        val log = ArrayList<Rect?>()
        val framesPerPress = (intervalMs / 16).toInt().coerceAtLeast(1)
        repeat(presses) {
            rule.onRoot().performKeyInput { pressKey(key) }
            repeat(framesPerPress) { frame(log) }
        }
        repeat(40) { frame(log) }
        return log
    }

    private fun report(name: String, log: List<Rect?>) {
        val xs = log.map { it?.left }
        val ys = log.map { it?.top }
        fun reversals(v: List<Float?>): Int {
            var count = 0
            var lastSign = 0
            for (i in 1 until v.size) {
                val a = v[i - 1] ?: continue
                val b = v[i] ?: continue
                val d = b - a
                if (abs(d) < 0.5f) continue
                val s = if (d > 0) 1 else -1
                if (lastSign != 0 && s != lastSign) count++
                lastSign = s
            }
            return count
        }
        // Frames tot alles stilstaat na de laatste toets.
        val settle = log.indices.reversed().firstOrNull { i -> i > 0 && log[i] != log[i - 1] }?.let { it + 1 } ?: 0
        println("=== $name: ${log.size} frames, x-omkeringen=${reversals(xs)}, y-omkeringen=${reversals(ys)}, laatste beweging in frame $settle")
        println("x: " + xs.joinToString(" ") { it?.toInt()?.toString() ?: "-" })
        println("y: " + ys.joinToString(" ") { it?.toInt()?.toString() ?: "-" })
    }

    private fun openTab(downs: Int) {
        // Zoals op een Android TV: dan gebruikt Compose het tv-scrollgedrag (focus op 30% van de hoogte).
        org.robolectric.Shadows.shadowOf(rule.activity.packageManager)
            .setSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK, true)
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        // Taalkeuze van de eerste start overslaan.
        rule.runOnUiThread { vm.setAppLanguage(nl.iptvplayer.app.i18n.AppLanguage.NL) }
        rule.runOnUiThread { vm.login("demo", "demo", "demo") }
        runCatching { rule.waitUntil(20_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                vm.state == AppState.Ready
            } }
            .onFailure { throw AssertionError("Inloggen demo mislukt: ${vm.state}", it) }
        rule.waitForIdle()
        // Zijbalk: Home heeft focus; omlaag naar het tabblad (focus = kiezen), dan naar rechts de inhoud in.
        repeat(downs) { rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }; rule.waitForIdle() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.mainClock.advanceTimeBy(1_000)
        frameDir?.mkdirs()
        rule.mainClock.autoAdvance = false
    }

    @Test
    fun series() {
        openTab(downs = 2)
        report("series: 1x rechts", hold(Key.DirectionRight, 1, intervalMs = 400))
        report("series: rechts ingedrukt", hold(Key.DirectionRight, 10))
        report("series: links ingedrukt", hold(Key.DirectionLeft, 6))
        report("series: 1x omlaag", hold(Key.DirectionDown, 1, intervalMs = 400))
        report("series: omlaag ingedrukt", hold(Key.DirectionDown, 8))
        report("series: omhoog ingedrukt", hold(Key.DirectionUp, 8))
        report("series: 1x omhoog", hold(Key.DirectionUp, 1, intervalMs = 400))
    }

    @Test
    fun movies() {
        openTab(downs = 3)
        report("films: rechts ingedrukt", hold(Key.DirectionRight, 12))
        report("films: omlaag ingedrukt", hold(Key.DirectionDown, 8))
        report("films: 1x omlaag", hold(Key.DirectionDown, 1, intervalMs = 400))
        report("films: omhoog ingedrukt", hold(Key.DirectionUp, 8))
    }
}
