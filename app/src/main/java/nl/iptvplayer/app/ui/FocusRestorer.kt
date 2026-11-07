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
 * Onthoudt welk element focus had, zodat na "terug" de focus op dezelfde poster terechtkomt
 * in plaats van bovenaan het scherm. Herstel gebeurt alleen kort na een terug-navigatie,
 * niet bij het wisselen van tabblad.
 */
class FocusRestorer internal constructor(private val saved: MutableState<String?>) {
    /** True als er nog niets bekend is (eerste bezoek). */
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

        /** Aanroepen bij terug-navigatie naar een vorig scherm. */
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
 * Vervanger voor Compose' focusRestorer() in scrollende lijsten. Die crasht in Compose 1.7
 * ("Release should only be called once") omdat hij lijst-items vastpint. Deze versie onthoudt
 * alleen welk item het doel is (bijv. de geselecteerde categorie) en stuurt de focus daarheen
 * als je de lijst binnenkomt.
 */
class ListFocusMemory internal constructor() {
    internal var target: FocusRequester? = null
}

@Composable
fun rememberListFocusMemory(): ListFocusMemory = remember { ListFocusMemory() }

/** Op de lijst zelf: bij binnenkomen naar het doel-item, anders gewoon het dichtstbijzijnde item. */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.enterFocusAt(memory: ListFocusMemory): Modifier =
    focusProperties { enter = { memory.target ?: FocusRequester.Default } }.focusGroup()

/** Op elk item: [isTarget] = dit item krijgt de focus als je de lijst binnenkomt. */
@Composable
fun Modifier.listFocusTarget(memory: ListFocusMemory, isTarget: Boolean): Modifier {
    val requester = remember { FocusRequester() }
    if (isTarget) {
        // Alleen zolang het item echt in beeld (gecomponeerd) is, anders zou de focus nergens heen kunnen.
        DisposableEffect(requester) {
            memory.target = requester
            onDispose { if (memory.target === requester) memory.target = null }
        }
    }
    return focusRequester(requester)
}

/** Vraagt focus aan zodra het element in beeld is. */
suspend fun FocusRequester.requestSafely() {
    withFrameNanos { }
    runCatching { requestFocus() }
}
