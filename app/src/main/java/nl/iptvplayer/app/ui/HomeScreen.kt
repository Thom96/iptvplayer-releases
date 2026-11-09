package nl.iptvplayer.app.ui

import nl.iptvplayer.app.i18n.S
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.ProfileMode
import nl.iptvplayer.app.R
import nl.iptvplayer.app.data.Kind

/** Order = order in the side rail, from top to bottom; settings sit separately at the bottom. */
enum class HomeTab(val icon: ImageVector) {
    SEARCH(Icons.Outlined.Search),
    HOME(Icons.Outlined.Home),
    LIVE(Icons.Outlined.LiveTv),
    SERIES(Icons.Outlined.VideoLibrary),
    MOVIES(Icons.Outlined.Movie),
    FAVORITES(Icons.Outlined.FavoriteBorder),
    CATCHUP(Icons.Outlined.History),
    SETTINGS(Icons.Outlined.Settings);

    val label: String get() = when (this) {
        SEARCH -> S.tabSearch
        HOME -> S.tabHome
        LIVE -> S.tabLive
        SERIES -> S.tabSeries
        MOVIES -> S.tabMovies
        FAVORITES -> S.tabFavorites
        CATCHUP -> S.tabCatchup
        SETTINGS -> S.tabSettings
    }
}

/** Width of the collapsed side rail; the content starts to the right of it. */
private val RailWidth = 76.dp
private val RailExpandedWidth = 248.dp
/** Nine tabs plus the profile avatar must fit within 540 dp: 20 + 44 + 4 + 10 × 40 + 12 × 4 + 20 = 536. */
private val RailItemHeight = 40.dp

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

    // Back: first to the side rail, then to Home, then close the app.
    BackHandler {
        when {
            !navFocused -> scope.launch { tabFocus.getValue(tab).requestSafely() }
            tab != HomeTab.HOME -> goTo(HomeTab.HOME)
            else -> activity?.finish()
        }
    }

    val showRail = !vm.liveFullscreen
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = if (showRail) RailWidth else 0.dp, top = if (showRail) 16.dp else 0.dp),
        ) {
            tabHolder.SaveableStateProvider(tab.name) {
                when (tab) {
                    HomeTab.HOME -> StartTab(vm, library)
                    HomeTab.MOVIES -> CategoryRowsTab(vm, library, Kind.MOVIE)
                    HomeTab.SERIES -> CategoryRowsTab(vm, library, Kind.SERIES)
                    HomeTab.LIVE -> LiveTab(vm, library)
                    HomeTab.FAVORITES -> FavoritesTab(vm, library)
                    HomeTab.CATCHUP -> CatchupTab(vm, library)
                    HomeTab.SEARCH -> SearchTab(vm, library)
                    HomeTab.SETTINGS -> if (vm.settingsLocked) SettingsLockedPane(vm) else SettingsTab(vm, vm.library ?: library)
                }
            }
        }
        if (showRail) {
            NavRail(
                current = tab,
                expanded = navFocused,
                tabFocus = tabFocus,
                onSelect = { tab = it },
                profile = if (vm.profiles.size > 1) {
                    { expanded -> RailProfileItem(vm, expanded) }
                } else null,
                modifier = Modifier
                    .zIndex(1f)
                    // From the content, always land on the active tab, not on the item that happens to be
                    // next to it: focus = select, so that would switch tabs right away.
                    .focusProperties { enter = { tabFocus.getValue(tab) } }
                    .focusGroup()
                    .onFocusChanged { navFocused = it.hasFocus },
            )
        }
    }
}

/**
 * Side rail from the brandbook: icons on the darkest layer, expands with labels as soon as it has focus.
 * Expanded it lies over the content, so the content doesn't jump.
 */
@Composable
private fun NavRail(
    current: HomeTab,
    expanded: Boolean,
    tabFocus: Map<HomeTab, FocusRequester>,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
    profile: (@Composable (expanded: Boolean) -> Unit)? = null,
) {
    val width by animateDpAsState(if (expanded) RailExpandedWidth else RailWidth, label = "rail")
    Box(modifier.fillMaxHeight().width(width)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Palette.rail),
        )
        Column(
            // Fits within 540 dp height (Chromecast with Google TV: 1920×1080 at density 2).
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.height(44.dp).padding(start = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                // Collapsed only the TV (the o), expanded the whole wordmark.
                if (expanded) {
                    GalloWordmark(size = 30.sp)
                } else {
                    Image(painterResource(R.drawable.gallo_o), contentDescription = "Gallo", modifier = Modifier.size(width = 36.dp, height = 33.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            HomeTab.entries.forEach { t ->
                if (t == HomeTab.SETTINGS) {
                    Spacer(Modifier.weight(1f))
                    profile?.invoke(expanded)
                }
                RailItem(
                    tab = t,
                    selected = t == current,
                    expanded = expanded,
                    onSelect = { onSelect(t) },
                    modifier = Modifier.focusRequester(tabFocus.getValue(t)),
                )
            }
        }
    }
}

/** Item in the side rail: active = silver white surface, focus = champagne border (as everywhere in the app). */
@Composable
private fun RailItem(tab: HomeTab, selected: Boolean, expanded: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onSelect,
        modifier = modifier
            .height(RailItemHeight)
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onSelect() }
            .tapToClick(onSelect),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.text else Color.Transparent,
            contentColor = if (selected) Palette.background else Palette.textMuted,
            focusedContainerColor = if (selected) Palette.text else Palette.surfaceFocused,
            focusedContentColor = if (selected) Palette.background else Palette.text,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Palette.accent), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(tab.icon, tab.label, Modifier.size(24.dp))
            if (expanded) {
                Text(
                    tab.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

/** Avatar of the current profile in the side rail (only with two or more profiles); opens "Who's watching?". */
@Composable
private fun RailProfileItem(vm: AppViewModel, expanded: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    val open = { vm.profileScreen = ProfileMode.PICK }
    Surface(
        onClick = open,
        modifier = Modifier.height(RailItemHeight).fillMaxWidth().tapToClick(open),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Palette.textMuted,
            focusedContainerColor = Palette.surfaceFocused,
            focusedContentColor = Palette.text,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Palette.accent), shape = shape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(vm.activeProfile, vm.profileName(vm.activeProfile), 26.dp)
            if (expanded) {
                Text(
                    vm.profileName(vm.activeProfile),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 15.dp),
                )
            }
        }
    }
}
