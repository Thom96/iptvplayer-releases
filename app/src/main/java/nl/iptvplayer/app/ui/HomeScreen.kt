package nl.iptvplayer.app.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import nl.iptvplayer.app.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.data.Kind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Volgorde = volgorde in de balk, zoals Netflix: logo, tekstlinks, en rechts de iconen.
enum class HomeTab(val label: String, val icon: ImageVector? = null) {
    HOME("Home"),
    LIVE("Live TV"),
    SERIES("Series"),
    MOVIES("Films"),
    FAVORITES("Favorieten"),
    CATCHUP("Terugkijken"),
    SEARCH("Zoeken", Icons.Outlined.Search),
    SETTINGS("Instellingen", Icons.Outlined.Settings),
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    val library = vm.visibleLibrary ?: return
    val activity = LocalContext.current as? Activity
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableStateOf(HomeTab.HOME) }
    var firstVisit by rememberSaveable { mutableStateOf(true) }
    var navFocused by remember { mutableStateOf(false) }
    val tabFocus = remember { HomeTab.entries.associateWith { FocusRequester() } }
    val tabHolder = rememberSaveableStateHolder()

    fun goTo(t: HomeTab) {
        tab = t
        scope.launch { tabFocus.getValue(t).requestSafely() }
    }

    LaunchedEffect(Unit) {
        if (firstVisit) {
            firstVisit = false
            tabFocus.getValue(tab).requestSafely()
        }
    }

    // Terug: eerst naar de navigatiebalk, dan naar Home, dan de app sluiten.
    BackHandler {
        when {
            !navFocused -> scope.launch { tabFocus.getValue(tab).requestSafely() }
            tab != HomeTab.HOME -> goTo(HomeTab.HOME)
            else -> activity?.finish()
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (!vm.liveFullscreen) {
        Row(
            Modifier.fillMaxWidth().padding(start = 56.dp, end = 56.dp, top = 24.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavLogo()
            Spacer(Modifier.width(20.dp))
            Row(
                Modifier
                    .weight(1f)
                    // Vanuit de inhoud altijd op het actieve tabblad uitkomen, niet op het tabblad dat er toevallig
                    // boven ligt: focus = selecteren, dus dat zou meteen van tabblad wisselen.
                    .focusProperties { enter = { tabFocus.getValue(tab) } }
                    .focusGroup()
                    .onFocusChanged { navFocused = it.hasFocus },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeTab.entries.forEach { t ->
                    val mod = Modifier.focusRequester(tabFocus.getValue(t))
                    when {
                        // Eerste icoon (zoeken) duwt de iconen naar rechts.
                        t == HomeTab.SEARCH -> {
                            Spacer(Modifier.weight(1f))
                            NavIconItem(t.icon!!, t.label, selected = t == tab, onSelect = { tab = t }, modifier = mod)
                        }
                        t.icon != null -> NavIconItem(t.icon, t.label, selected = t == tab, onSelect = { tab = t }, modifier = mod)
                        else -> NavTextItem(t.label, selected = t == tab, onSelect = { tab = t }, modifier = mod)
                    }
                }
            }
        }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            tabHolder.SaveableStateProvider(tab.name) {
                when (tab) {
                    HomeTab.HOME -> StartTab(vm, library)
                    HomeTab.MOVIES -> CategoryRowsTab(vm, library, Kind.MOVIE)
                    HomeTab.SERIES -> CategoryRowsTab(vm, library, Kind.SERIES)
                    HomeTab.LIVE -> LiveTab(vm, library)
                    HomeTab.FAVORITES -> FavoritesTab(vm, library)
                    HomeTab.CATCHUP -> CatchupTab(vm, library)
                    HomeTab.SEARCH -> SearchTab(vm, library)
                    HomeTab.SETTINGS -> SettingsTab(vm, vm.library ?: library)
                }
            }
        }
    }
}

/** Tabblad als pil, zoals Google TV: wit bij focus, licht grijs als het actief is. */
@Composable
private fun NavTextItem(label: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onSelect,
        modifier = modifier
            .padding(horizontal = 3.dp)
            .onFocusChanged { if (it.isFocused) onSelect() }
            .tapToClick(onSelect),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent,
            contentColor = if (selected) Palette.text else Palette.textMuted,
            focusedContainerColor = Palette.text,
            focusedContentColor = Palette.background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun NavIconItem(icon: ImageVector, label: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onSelect,
        modifier = modifier
            .padding(horizontal = 4.dp)
            .size(48.dp)
            .onFocusChanged { if (it.isFocused) onSelect() }
            .tapToClick(onSelect),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent,
            contentColor = if (selected) Palette.text else Palette.textMuted,
            focusedContainerColor = Palette.text,
            focusedContentColor = Palette.background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Icon(icon, label, Modifier.align(Alignment.Center).size(24.dp))
    }
}

/** Logo linksboven, zoals bij Netflix: tv-icoon + "PopcornTime" in de kleuren van de banner. */
@Composable
private fun NavLogo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(40.dp))
        Text("Popcorn", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFFF3E0))
        Text("Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFFC93C))
    }
}
