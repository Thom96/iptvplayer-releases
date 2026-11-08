package nl.iptvplayer.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import nl.iptvplayer.app.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The loading screen lets the rooster finish his moment: one crow on a fast start (just over 0.7 s), and after
 * downloading the last grains and a crow. Then he reports done exactly once.
 *
 * With -Pframes=/path, snapshots of the fast loading screen are saved.
 */
@RunWith(RobolectricTestRunner::class)
class LoadingRoosterTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val frameDir = System.getProperty("scrollFrames")?.takeIf { it.isNotBlank() }?.let(::File)

    private fun show(state: AppState.Loading, onDone: () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { AppTheme { LoadingScreen(state, onRoosterDone = onDone) } }
    }

    /** Runs the clock until [onDone] is called; returns the elapsed time in ms. */
    private fun timeUntilDone(calls: () -> Int, maxMs: Long, snapshotsAt: Set<Long> = emptySet(), name: String = ""): Long {
        var t = 0L
        while (calls() == 0 && t < maxMs) {
            rule.mainClock.advanceTimeBy(16)
            t += 16
            if (snapshotsAt.any { it in (t - 15)..t }) snapshot("$name-${t}ms")
        }
        return t
    }

    private fun snapshot(name: String) {
        val dir = frameDir ?: return
        dir.mkdirs()
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bmp))
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun quickStartCrowsOnceThenFinishes() {
        var calls = 0
        show(AppState.Loading("", 1f, quick = true, finished = true)) { calls++ }
        val t = timeUntilDone({ calls }, 3_000, setOf(80L, 400L, 600L), "quick")
        assertTrue("done after $t ms", t in 700..1_100)
        rule.mainClock.advanceTimeBy(3_000)
        assertEquals("reported done only once", 1, calls)
    }

    @Test
    fun fullLoadEatsRemainingGrainsAndCrows() {
        var calls = 0
        show(AppState.Loading("", 1f, quick = false, finished = true)) { calls++ }
        val t = timeUntilDone({ calls }, 6_000, setOf(1_000L, 2_900L), "full")
        // All ten grains behind at once: run and crow. In practice he mostly keeps up during the download;
        // AppViewModel waits at most 2.5 s.
        assertTrue("done after $t ms", t in 1_000..4_000)
        assertEquals(1, calls)
    }

    @Test
    fun waitsWhileStillLoading() {
        var calls = 0
        show(AppState.Loading("", 0.3f, quick = true, finished = false)) { calls++ }
        rule.mainClock.advanceTimeBy(3_000)
        assertEquals("not done while still loading", 0, calls)
    }
}
