package nl.iptvplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import nl.iptvplayer.app.ui.ActivationScreen
import nl.iptvplayer.app.ui.AppTheme
import nl.iptvplayer.app.ui.FailedScreen
import nl.iptvplayer.app.ui.FocusRestorer
import nl.iptvplayer.app.ui.HomeScreen
import nl.iptvplayer.app.ui.LanguageScreen
import nl.iptvplayer.app.ui.LoadingScreen
import nl.iptvplayer.app.ui.LoginScreen
import nl.iptvplayer.app.ui.MovieDetailScreen
import nl.iptvplayer.app.ui.SeriesDetailScreen
import nl.iptvplayer.app.ui.BrowseTab
import nl.iptvplayer.app.ui.UpdatePrompt

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                if (!vm.languageChosen) {
                    LanguageScreen(vm)
                } else {
                    when (val s = vm.state) {
                        is AppState.Login -> LoginScreen(vm, s)
                        is AppState.Loading -> LoadingScreen(s, onRoosterDone = vm::loadingAnimationDone)
                        is AppState.Failed -> FailedScreen(s.message, onRetry = vm::retry, onLogout = vm::logout)
                        AppState.Ready -> when (val gate = vm.gate) {
                            Gate.NONE -> Navigation(vm)
                            else -> ActivationScreen(vm, gate)
                        }
                    }
                    UpdatePrompt(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from the "allow unknown apps" screen: resume updating.
        vm.retryAfterPermission()
        // Stayed in memory for a long time: refresh lists and check for updates if that was long ago.
        vm.onAppResumed()
        // "Episodes and more" in the player: open the series page.
        val app = application as App
        app.pendingSeriesDetail?.let { key ->
            app.pendingSeriesDetail = null
            vm.library?.byKey?.get(key)?.let { vm.nav.add(Screen.Series(it)) }
        }
    }
}

@Composable
private fun Navigation(vm: AppViewModel) {
    val holder = rememberSaveableStateHolder()
    val screen = vm.nav.last()

    BackHandler(enabled = vm.nav.size > 1) {
        vm.back()?.let { holder.removeState(it.key) }
        FocusRestorer.markBackNavigation()
    }

    holder.SaveableStateProvider(screen.key) {
        when (screen) {
            Screen.Home -> HomeScreen(vm)
            is Screen.Movie -> MovieDetailScreen(vm, screen.item)
            is Screen.Series -> SeriesDetailScreen(vm, screen.item)
            is Screen.Browse -> vm.visibleLibrary?.let { BrowseTab(vm, it, screen.kind, screen.categoryId) }
        }
    }
}
