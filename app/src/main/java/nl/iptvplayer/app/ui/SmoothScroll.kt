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
 * Stiffness of all movements while browsing (scrolling, expanding cards and the info panel).
 * Critically damped springs with the same stiffness add up to one smooth movement, even when you
 * hold an arrow key down: a spring keeps its velocity on a new target, a tween starts
 * from standstill every time and then jerks. ~0.25 s to standstill.
 */
const val MOVE_STIFFNESS = 700f

fun <T> moveSpring(visibilityThreshold: T? = null): SpringSpec<T> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = MOVE_STIFFNESS, visibilityThreshold = visibilityThreshold)

/** No automatic scrolling to the focus: the list does that itself with a [SpringScroller]. */
@OptIn(ExperimentalFoundationApi::class)
object NoAutoScroll : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

/**
 * Scrolls a list with a spring like [moveSpring]. The target is read from the layout again every frame,
 * so cards and panels that are still expanding or collapsing always count correctly. A new
 * command during a running movement takes over the velocity: holding a key gives a
 * continuous movement instead of jerks.
 */
class SpringScroller internal constructor(private val state: ScrollableState, private val scope: CoroutineScope) {
    private var job: Job? = null
    private var velocity = 0f

    /** [remaining]: how much still needs to be scrolled (px), or null if the target is off screen. */
    fun follow(remaining: () -> Float?) {
        job?.cancel()
        job = scope.launch {
            state.scroll {
                val stiffness = MOVE_STIFFNESS
                val damping = 2f * sqrt(stiffness) // critically damped: no overshoot
                var lastFrame = 0L
                while (true) {
                    val now = withFrameNanos { it }
                    var dt = if (lastFrame == 0L) 1f / 60 else ((now - lastFrame) / 1e9f).coerceIn(0f, 0.05f)
                    lastFrame = now
                    val distance = remaining() ?: break
                    // Compute in small steps: stable, even when a frame comes a bit late.
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
                    if (abs(delta - consumed) > 0.5f) break // start or end of the list
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
