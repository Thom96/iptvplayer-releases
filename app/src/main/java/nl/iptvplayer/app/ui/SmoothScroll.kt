package nl.iptvplayer.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.ScrollableState
import android.os.SystemClock
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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

/**
 * Keeps the focused element of a list or grid at a fixed height: its top edge sits at [fraction] of the
 * visible height and the content slides underneath. Your eyes don't have to follow the focus down the screen.
 * At the start and end of the list it stops at the edge. Elements taller than [LARGE] of the screen are only
 * brought into view as far as needed.
 */
@OptIn(ExperimentalFoundationApi::class)
class PivotBringIntoView(
    private val fraction: Float,
    private val canScrollBackward: () -> Boolean,
    private val canScrollForward: () -> Boolean,
) : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = moveSpring()

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (size >= containerSize * LARGE) return justIntoView(offset, size, containerSize)
        val distance = offset - containerSize * fraction
        // Without this the list keeps asking for a scroll it cannot do at its edges, which keeps a weak box busy.
        if (distance < 0f && !canScrollBackward()) return 0f
        if (distance > 0f && !canScrollForward()) return 0f
        return if (abs(distance) < 2f) 0f else distance
    }

    private fun justIntoView(offset: Float, size: Float, containerSize: Float): Float {
        val end = offset + size
        return when {
            offset >= 0f && end <= containerSize -> 0f
            offset < 0f && end > containerSize -> 0f
            offset < 0f -> offset
            else -> end - containerSize
        }
    }

    private companion object {
        const val LARGE = 0.6f
    }
}

/** Tracks whether an arrow key is being held down, so heavy work (previews, guide data) can wait for the release. */
object HeldKey {
    @Volatile
    private var lastRepeatAt = 0L

    internal fun onRepeat(now: Long) {
        lastRepeatAt = now
    }

    /** True while an arrow key is held: a key repeat came in within the last 300 ms. */
    val isHeld: Boolean get() = SystemClock.uptimeMillis() - lastRepeatAt < 300
}

private val ARROW_KEYS = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)

/** Minimum time between two steps while an arrow key is held (~9 steps per second). */
const val HELD_KEY_STEP_MS = 110L

/**
 * Holding an arrow key: lets at most one step through every [HELD_KEY_STEP_MS]. A remote repeats about 20 times per
 * second; that floods a slow box with focus moves and scroll animations, which makes the list stutter. A single
 * press is never delayed.
 */
@Composable
fun Modifier.calmHeldKeys(): Modifier {
    val lastStep = remember { LongArray(1) }
    return onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown || e.key !in ARROW_KEYS || e.nativeKeyEvent.repeatCount == 0) return@onPreviewKeyEvent false
        val now = SystemClock.uptimeMillis()
        HeldKey.onRepeat(now)
        if (now - lastStep[0] < HELD_KEY_STEP_MS) {
            true
        } else {
            lastStep[0] = now
            false
        }
    }
}
