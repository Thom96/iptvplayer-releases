package nl.iptvplayer.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Stijfheid van alle bewegingen bij het bladeren (scrollen, uitklappen van kaart en infopaneel).
 * Kritisch gedempte veren met dezelfde stijfheid tellen op tot één vloeiende beweging, ook als je
 * een pijltje ingedrukt houdt: een veer neemt bij een nieuw doel zijn snelheid mee, een tween begint
 * telkens opnieuw vanuit stilstand en schokt dan. ~0,25 s tot stilstand.
 */
const val MOVE_STIFFNESS = 700f

fun <T> moveSpring(visibilityThreshold: T? = null): SpringSpec<T> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = MOVE_STIFFNESS, visibilityThreshold = visibilityThreshold)

/** Geen automatisch scrollen naar de focus: dat doet de lijst zelf met een [SpringScroller]. */
@OptIn(ExperimentalFoundationApi::class)
object NoAutoScroll : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

/**
 * Scrollt een lijst met een veer zoals [moveSpring]. Het doel wordt elk frame opnieuw uit de layout
 * gelezen, zodat kaarten en panelen die nog in- of uitklappen altijd goed meetellen. Een nieuwe
 * opdracht tijdens een lopende beweging neemt de snelheid over: ingedrukt houden geeft een
 * doorlopende beweging in plaats van horten.
 */
class SpringScroller internal constructor(private val state: ScrollableState, private val scope: CoroutineScope) {
    private var job: Job? = null
    private var velocity = 0f

    /** [remaining]: hoeveel er nog gescrold moet worden (px), of null als het doel uit beeld is. */
    fun follow(remaining: () -> Float?) {
        job?.cancel()
        job = scope.launch {
            state.scroll {
                val stiffness = MOVE_STIFFNESS
                val damping = 2f * sqrt(stiffness) // kritisch gedempt: geen doorschieten
                var lastFrame = 0L
                while (true) {
                    val now = withFrameNanos { it }
                    var dt = if (lastFrame == 0L) 1f / 60 else ((now - lastFrame) / 1e9f).coerceIn(0f, 0.05f)
                    lastFrame = now
                    val distance = remaining() ?: break
                    // In kleine stapjes rekenen: stabiel, ook als een frame wat later komt.
                    var target = distance
                    var delta = 0f
                    while (dt > 0f) {
                        val step = minOf(dt, 0.004f)
                        velocity += (stiffness * target - damping * velocity) * step
                        val d = velocity * step
                        delta += d
                        target -= d
                        dt -= step
                    }
                    val consumed = scrollBy(delta)
                    if (abs(delta - consumed) > 0.5f) break // begin of eind van de lijst
                    if (abs(distance - consumed) < 0.5f && abs(velocity) < 20f) break
                }
            }
            velocity = 0f
        }
    }
}

@Composable
fun rememberSpringScroller(state: ScrollableState): SpringScroller {
    val scope = rememberCoroutineScope()
    return remember(state) { SpringScroller(state, scope) }
}
