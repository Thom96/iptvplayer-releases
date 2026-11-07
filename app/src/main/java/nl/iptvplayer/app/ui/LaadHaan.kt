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
 * Laadbalk met de Gallo-haan: hij loopt langs een rij korrels en pikt er één op voor elk deel van het laden dat
 * klaar is ([progress], 0..1). De balk loopt mee met wat hij al gegeten heeft. Wacht hij op het laden, dan
 * knippert hij en scharrelt hij wat rond; is alles binnen, dan kraait hij.
 *
 * Met [quick] (snelle start uit de bewaarde lijsten) is er geen balk: de haan staat in het midden en kraait
 * meteen één keer. [onFinished] volgt één keer zodra het laden klaar is ([finished]) en de haan heeft gekraaid;
 * dan mag het laadscherm weg. Loopt hij achter als het laden klaar is, dan rent hij naar de laatste korrels.
 *
 * De houdingen zijn plaatjes uit tools/haan_sprites.py (80 dp, voeten-midden op 40 × 70 dp).
 */
@Composable
fun LaadHaan(
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
    var x by remember { mutableFloatStateOf(if (quick) QUICK_X else START_X) } // voeten-midden, in dp
    var pose by remember { mutableStateOf(R.drawable.haan_staan) }

    LaunchedEffect(quick) {
        var idleTicks = 0
        var crowed = false
        var reported = false
        suspend fun crow(times: Int) {
            pose = R.drawable.haan_kraai_1; delay(120)
            repeat(times) { pose = R.drawable.haan_kraai_2; delay(110); pose = R.drawable.haan_kraai_1; delay(110) }
            pose = R.drawable.haan_staan
            crowed = true
        }
        while (true) {
            if (crowed && done && !reported) {
                reported = true
                notifyFinished()
            }
            if (quick) {
                if (!crowed) {
                    delay(150) // eerst even staan, dan kraaien
                    crow(QUICK_CROWS)
                } else {
                    idleTicks++
                    if (idleTicks % 12 == 4) { pose = R.drawable.haan_knipper; delay(140); pose = R.drawable.haan_staan }
                    delay(150)
                }
            } else if (eaten < target) {
                // Naar de volgende korrel lopen; sneller als hij achterloopt, rennen als het laden al klaar is.
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
                pose = R.drawable.haan_staan
                delay(tick)
                idleTicks = 0
            } else if (eaten >= GRAINS) {
                crow(4)
                if (done && !reported) continue // meteen melden, de pauze hieronder is voor het herhalen
                delay(900)
            } else {
                // Wachten: af en toe knipperen of in de grond pikken.
                idleTicks++
                when (idleTicks % 12) {
                    4 -> { pose = R.drawable.haan_knipper; delay(140); pose = R.drawable.haan_staan }
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
                // Balk: spoor en het deel dat al opgegeten is.
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
    show(R.drawable.haan_pik_1); delay(tick)
    show(R.drawable.haan_pik_2); delay(tick * 2)
    show(R.drawable.haan_pik_1); delay(tick)
}

private val WALK = listOf(R.drawable.haan_loop_0, R.drawable.haan_loop_1, R.drawable.haan_loop_2, R.drawable.haan_loop_3)

private const val GRAINS = 10
private const val BAR_LEFT = 20f
private const val BAR_RIGHT = 300f
private const val BAR_Y = 93f
private const val GRAIN_Y = 87f
private const val FEET_Y = 88f
private const val BEAK_DX = 29.5f // snavelpunt bij het pikken, rechts van de voeten
private const val STEP_DP = 5.75f // afstand per loopstand
private const val START_X = 60f - BEAK_DX
private const val QUICK_X = 160f // midden van het vak
private const val QUICK_CROWS = 3 // kraai van ruim 0,7 s

private fun grainX(i: Int) = 80f + i * 23f

private val GRAIN = Color(0xFFCCAC70)
private val SHADOW = Color(0xFF050E13)
