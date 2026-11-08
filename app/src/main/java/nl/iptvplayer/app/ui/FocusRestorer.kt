package nl.iptvplayer.app.ui

import android.os.SystemClock
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusProperties
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Remembers which element had focus, so that after "back" the focus lands on the same poster
 * instead of at the top of the screen. Restoring only happens shortly after a back navigation,
 * not when switching tabs.
 */
class FocusRestorer internal constructor(private val saved: MutableState<String?>) {
    /** True if nothing is known yet (first visit). */
    val isFresh: Boolean get() = saved.value == null

    internal fun onFocused(key: String) {
        saved.value = key
    }

    internal fun consume(key: String): Boolean {
        if (saved.value == key && SystemClock.uptimeMillis() - lastBackAt < RESTORE_WINDOW_MS) {
            lastBackAt = 0
            return true
        }
        return false
    }

    companion object {
        private const val RESTORE_WINDOW_MS = 2_000L
        private var lastBackAt = 0L

        /** Call on back navigation to a previous screen. */
        fun markBackNavigation() {
            lastBackAt = SystemClock.uptimeMillis()
        }
    }
}

@Composable
fun rememberFocusRestorer(): FocusRestorer {
    val saved = rememberSaveable { mutableStateOf<String?>(null) }
    return remember { FocusRestorer(saved) }
}

@Composable
fun Modifier.restoreFocus(restorer: FocusRestorer, key: String): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(key) {
        if (restorer.consume(key)) requester.requestSafely()
    }
    return this
        .focusRequester(requester)
        .onFocusChanged { if (it.isFocused) restorer.onFocused(key) }
}

/**
 * Replacement for Compose's focusRestorer() in scrolling lists. That crashes in Compose 1.7
 * ("Release should only be called once") because it pins list items. This version only remembers
 * which item is the target (e.g. the selected category) and sends the focus there
 * when you enter the list.
 */
class ListFocusMemory internal constructor() {
    internal var target: FocusRequester? = null
}

@Composable
fun rememberListFocusMemory(): ListFocusMemory = remember { ListFocusMemory() }

/** On the list itself: on entering, go to the target item, otherwise just the nearest item. */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.enterFocusAt(memory: ListFocusMemory): Modifier =
    focusProperties { enter = { memory.target ?: FocusRequester.Default } }.focusGroup()

/** On every item: [isTarget] = this item gets the focus when you enter the list. */
@Composable
fun Modifier.listFocusTarget(memory: ListFocusMemory, isTarget: Boolean): Modifier {
    val requester = remember { FocusRequester() }
    if (isTarget) {
        // Only while the item is really on screen (composed), otherwise the focus would have nowhere to go.
        DisposableEffect(requester) {
            memory.target = requester
            onDispose { if (memory.target === requester) memory.target = null }
        }
    }
    return focusRequester(requester)
}

/** Requests focus as soon as the element is on screen. */
suspend fun FocusRequester.requestSafely() {
    withFrameNanos { }
    runCatching { requestFocus() }
}
