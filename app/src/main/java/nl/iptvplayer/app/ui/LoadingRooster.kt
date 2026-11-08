package nl.iptvplayer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nl.iptvplayer.app.R
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Progress bar with the Gallo rooster: he walks along a row of grains and pecks one up for every part of the
 * loading that is done ([progress], 0..1). The bar follows what he has already eaten. While waiting for the
 * loading he blinks and scratches around a bit; once everything is in, he crows.
 *
 * With [quick] (fast start from the saved lists) there is no bar: the rooster stands in the middle and crows
 * once right away. [onFinished] fires once as soon as loading is done ([finished]) and the rooster has crowed;
 * then the loading screen may go away. If he is behind when loading is done, he runs to the last grains.
 *
 * The poses are images from tools/rooster_sprites.py (80 dp, centre of the feet at 40 × 70 dp).
 */
@Composable
fun LoadingRooster(
    progress: Float,
    modifier: Modifier = Modifier,
    quick: Boolean = false,
    finished: Boolean = false,
    onFinished: () -> Unit = {},
) {
    val target by rememberUpdatedState((progress.coerceIn(0f, 1f) * GRAINS).toInt())
    val done by rememberUpdatedState(finished)
    val notifyFinished by rememberUpdatedState(onFinished)
    var eaten by remember { mutableIntStateOf(0) }
    var x by remember { mutableFloatStateOf(if (quick) QUICK_X else START_X) } // centre of the feet, in dp
    var pose by remember { mutableStateOf(R.drawable.rooster_stand) }

    LaunchedEffect(quick) {
        var idleTicks = 0
        var crowed = false
        var reported = false
        suspend fun crow(times: Int) {
            pose = R.drawable.rooster_crow_1; delay(120)
            repeat(times) { pose = R.drawable.rooster_crow_2; delay(110); pose = R.drawable.rooster_crow_1; delay(110) }
            pose = R.drawable.rooster_stand
            crowed = true
        }
        while (true) {
            if (crowed && done && !reported) {
                reported = true
                notifyFinished()
            }
            if (quick) {
                if (!crowed) {
                    delay(150) // stand still for a moment, then crow
                    crow(QUICK_CROWS)
                } else {
                    idleTicks++
                    if (idleTicks % 12 == 4) { pose = R.drawable.rooster_blink; delay(140); pose = R.drawable.rooster_stand }
                    delay(150)
                }
            } else if (eaten < target) {
                // Walk to the next grain; faster when behind, running when loading is already done.
                val behind = target - eaten
                val tick = when {
                    done -> 25L
                    behind > 2 -> 45L
                    else -> 70L
                }
                val goal = grainX(eaten) - BEAK_DX
                val steps = max(2, ((goal - x) / STEP_DP).roundToInt())
                val from = x
                for (i in 1..steps) {
                    pose = WALK[(i - 1) % WALK.size]
                    x = from + (goal - from) * i / steps
                    delay(tick)
                }
                peck(tick) { pose = it }
                eaten++
                pose = R.drawable.rooster_stand
                delay(tick)
                idleTicks = 0
            } else if (eaten >= GRAINS) {
                crow(4)
                if (done && !reported) continue // report right away; the pause below is for repeating
                delay(900)
            } else {
                // Waiting: now and then blink or peck at the ground.
                idleTicks++
                when (idleTicks % 12) {
                    4 -> { pose = R.drawable.rooster_blink; delay(140); pose = R.drawable.rooster_stand }
                    9 -> peck(80L) { pose = it }
                }
                delay(150)
            }
        }
    }

    Box(modifier.size(320.dp, 100.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val u = 1.dp.toPx()
            if (!quick) {
                // Bar: track and the part that has been eaten already.
                val left = BAR_LEFT * u
                val right = BAR_RIGHT * u
                val r = CornerRadius(2 * u)
                drawRoundRect(Palette.surfaceFocused, Offset(left, (BAR_Y - 2) * u), Size(right - left, 4 * u), r)
                if (eaten > 0) {
                    val filled = if (eaten >= GRAINS) right else grainX(eaten - 1) * u
                    drawRoundRect(Palette.accent, Offset(left, (BAR_Y - 2) * u), Size(filled - left, 4 * u), r)
                }
                for (i in eaten until GRAINS) {
                    drawOval(GRAIN, Offset((grainX(i) - 2.5f) * u, (GRAIN_Y - 2) * u), Size(5 * u, 4 * u))
                }
            }
            drawOval(SHADOW, Offset((x - 20) * u, (FEET_Y + 0.5f - 2) * u), Size(40 * u, 4 * u))
        }
        Image(
            painterResource(pose),
            contentDescription = null,
            modifier = Modifier.size(80.dp).offset((x - 40).dp, (FEET_Y - 70).dp),
        )
    }
}

private suspend fun peck(tick: Long, show: (Int) -> Unit) {
    show(R.drawable.rooster_peck_1); delay(tick)
    show(R.drawable.rooster_peck_2); delay(tick * 2)
    show(R.drawable.rooster_peck_1); delay(tick)
}

private val WALK = listOf(R.drawable.rooster_walk_0, R.drawable.rooster_walk_1, R.drawable.rooster_walk_2, R.drawable.rooster_walk_3)

private const val GRAINS = 10
private const val BAR_LEFT = 20f
private const val BAR_RIGHT = 300f
private const val BAR_Y = 93f
private const val GRAIN_Y = 87f
private const val FEET_Y = 88f
private const val BEAK_DX = 29.5f // beak tip while pecking, right of the feet
private const val STEP_DP = 5.75f // distance per walking pose
private const val START_X = 60f - BEAK_DX
private const val QUICK_X = 160f // middle of the box
private const val QUICK_CROWS = 3 // a crow of just over 0.7 s

private fun grainX(i: Int) = 80f + i * 23f

private val GRAIN = Color(0xFFCCAC70)
private val SHADOW = Color(0xFF050E13)
