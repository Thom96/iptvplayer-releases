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
 * Het laadscherm laat de haan zijn moment afmaken: bij een snelle start één kraai (ruim 0,7 s), na het
 * downloaden de laatste korrels en een kraai. Daarna meldt hij zich één keer klaar.
 *
 * Met -Pframes=/pad worden momentopnamen van het snelle laadscherm bewaard.
 */
@RunWith(RobolectricTestRunner::class)
class LaadHaanTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val frameDir = System.getProperty("scrollFrames")?.takeIf { it.isNotBlank() }?.let(::File)

    private fun show(state: AppState.Loading, onDone: () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { AppTheme { LoadingScreen(state, onHaanDone = onDone) } }
    }

    /** Laat de klok lopen tot [onDone] geroepen is; geeft de verstreken tijd in ms. */
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
        val t = timeUntilDone({ calls }, 3_000, setOf(80L, 400L, 600L), "snel")
        assertTrue("klaar na $t ms", t in 700..1_100)
        rule.mainClock.advanceTimeBy(3_000)
        assertEquals("maar één keer klaar gemeld", 1, calls)
    }

    @Test
    fun fullLoadEatsRemainingGrainsAndCrows() {
        var calls = 0
        show(AppState.Loading("", 1f, quick = false, finished = true)) { calls++ }
        val t = timeUntilDone({ calls }, 6_000, setOf(1_000L, 2_900L), "volledig")
        // Alle tien korrels in één keer achter: rennen en kraaien. In het echt loopt hij grotendeels bij
        // tijdens het downloaden; AppViewModel wacht hooguit 2,5 s.
        assertTrue("klaar na $t ms", t in 1_000..4_000)
        assertEquals(1, calls)
    }

    @Test
    fun waitsWhileStillLoading() {
        var calls = 0
        show(AppState.Loading("", 0.3f, quick = true, finished = false)) { calls++ }
        rule.mainClock.advanceTimeBy(3_000)
        assertEquals("niet klaar zolang het laden loopt", 0, calls)
    }
}
