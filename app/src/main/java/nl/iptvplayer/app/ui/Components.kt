package nl.iptvplayer.app.ui

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import nl.iptvplayer.app.data.MediaItem
import nl.iptvplayer.app.data.Progress

private val CardShape = RoundedCornerShape(10.dp)
private val SELECT_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
private const val LONG_PRESS_MS = 600L

/**
 * Muis/touch: tikken = focus + klikken, lang indrukken = lange klik. De TV-componenten reageren
 * alleen op de afstandsbediening; dit maakt de app ook met de muis in de emulator bedienbaar.
 */
@Composable
fun Modifier.tapToClick(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier {
    val requester = remember { FocusRequester() }
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    val pressedAt = remember { longArrayOf(0) }
    val longPress = remember { booleanArrayOf(false) }
    return this
        .focusRequester(requester)
        .onPreviewKeyEvent { e ->
            // OK lang indrukken zelf afhandelen: veel afstandsbedieningen sturen niet het signaal
            // waarop de TV-componenten een lange klik herkennen, waardoor het item gewoon opende.
            // Pas bij loslaten reageren, zodat het loslaten niet in een geopende pop-up terechtkomt.
            if (longClick == null || e.key !in SELECT_KEYS) return@onPreviewKeyEvent false
            val native = e.nativeKeyEvent
            when (e.type) {
                KeyEventType.KeyDown -> when {
                    native.repeatCount == 0 -> {
                        pressedAt[0] = SystemClock.uptimeMillis()
                        longPress[0] = false
                    }
                    native.isLongPress -> longPress[0] = true
                }
                KeyEventType.KeyUp -> {
                    // Alleen als het indrukken hier begon (niet het loslaten na OK in een pop-up).
                    if (pressedAt[0] > 0) {
                        val held = SystemClock.uptimeMillis() - pressedAt[0]
                        pressedAt[0] = 0
                        if (longPress[0] || held >= LONG_PRESS_MS) longClick?.invoke() else click()
                    }
                }
            }
            true
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = {
                    runCatching { requester.requestFocus() }
                    click()
                },
                onLongPress = { longClick?.invoke() },
            )
        }
}

@Composable
private fun focusBorder() = CardDefaults.border(
    focusedBorder = Border(BorderStroke(3.dp, Palette.accent), shape = CardShape),
)

/** Poster voor een film of serie (2:3). Lang indrukken = favoriet aan/uit. */
@Composable
fun PosterCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** null = volledige breedte (voor rasters). */
    width: Dp? = 128.dp,
    isFavorite: Boolean = false,
    isWatched: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Column(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.tapToClick(onClick, onLongClick).fillMaxWidth().aspectRatio(2f / 3f),
            shape = CardDefaults.shape(CardShape),
            scale = CardDefaults.scale(focusedScale = 1.05f),
            border = focusBorder(),
            colors = CardDefaults.colors(containerColor = Palette.surface),
        ) {
            Box(Modifier.fillMaxSize()) {
                TitleFallback(item.title)
                AsyncImage(
                    model = item.image,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Row(
                    Modifier.align(Alignment.TopEnd).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isWatched) Badge(Icons.Filled.CheckCircle, Palette.accent)
                    if (isFavorite) Badge(Icons.Filled.Favorite, Palette.favorite)
                }
                if (item.rating > 0) {
                    Row(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Star, null, Modifier.size(12.dp), tint = Palette.gold)
                        Spacer(Modifier.width(3.dp))
                        Text("%.1f".format(item.rating), fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.year?.let {
            Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = Palette.textMuted)
        }
    }
}

@Composable
private fun Badge(icon: ImageVector, tint: Color) {
    Box(
        Modifier.background(Color(0xCC000000), RoundedCornerShape(50)).padding(4.dp),
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = tint)
    }
}

@Composable
private fun TitleFallback(name: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.surfaceFocused, Palette.surface)))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(name, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, maxLines = 4)
    }
}

/** Zenderkaart met logo (breed). */
@Composable
fun ChannelCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.tapToClick(onClick, onLongClick).fillMaxWidth().height(96.dp),
        shape = CardDefaults.shape(CardShape),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = focusBorder(),
        colors = CardDefaults.colors(containerColor = Palette.surface, focusedContainerColor = Palette.surfaceFocused),
    ) {
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 96.dp, height = 64.dp), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = item.image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isFavorite) Icon(Icons.Filled.Favorite, null, Modifier.size(16.dp), tint = Palette.favorite)
        }
    }
}

/** Kaart voor "Verder kijken" met voortgangsbalk. */
@Composable
fun ProgressCard(progress: Progress, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null) {
    Column(Modifier.width(260.dp)) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.tapToClick(onClick, onLongClick).fillMaxWidth().aspectRatio(16f / 9f),
            shape = CardDefaults.shape(CardShape),
            scale = CardDefaults.scale(focusedScale = 1.06f),
            border = focusBorder(),
            colors = CardDefaults.colors(containerColor = Palette.surface),
        ) {
            Box(Modifier.fillMaxSize()) {
                TitleFallback(progress.title)
                AsyncImage(
                    model = progress.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Klaargezette volgende aflevering (nog niet gestart) heeft geen voortgangsbalk.
                if (progress.durationMs > 0) ProgressBar(progress.fraction, Modifier.align(Alignment.BottomCenter))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(progress.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val subtitle = when {
            progress.durationMs == 0L -> "Volgende: ${progress.subtitle.orEmpty()}"
            progress.subtitle != null -> "${progress.subtitle} · nog ${formatRemaining(progress)}"
            else -> "Nog ${formatRemaining(progress)}"
        }
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).background(Palette.text.copy(alpha = 0.2f))) {
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(Palette.accent))
    }
}

/** Selecteerbare knop voor categorieën, sorteringen en seizoenen: actief = zilverwit, focus = champagne rand. */
@Composable
fun ChipButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    fill: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.text else Color.Transparent,
            contentColor = if (selected) Palette.background else Palette.textMuted,
            focusedContainerColor = if (selected) Palette.text else Palette.surfaceFocused,
            focusedContentColor = if (selected) Palette.background else Palette.text,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Palette.accent), shape = RoundedCornerShape(12.dp)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Row(
            Modifier
                .then(if (fill) Modifier.fillMaxWidth() else Modifier)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (fill) Modifier.weight(1f) else Modifier,
            )
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                Text(trailing, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun ActionButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    // Brandbook: pil; primair = zilverwit, secundair = lichte laag; focus = champagne rand.
    Button(
        onClick = onClick,
        modifier = modifier.tapToClick(onClick),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) Palette.text else Palette.surfaceFocused,
            contentColor = if (primary) Palette.background else Palette.text,
            focusedContainerColor = Palette.text,
            focusedContentColor = Palette.background,
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Palette.accent), shape = RoundedCornerShape(50)),
        ),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}

/** Tekstveld voor de afstandsbediening: OK opent het toetsenbord. */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    leading: ImageVector? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Palette.text, fontSize = 20.sp),
        cursorBrush = SolidColor(Palette.accent),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboardType,
            imeAction = imeAction,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            // Het tekstveld vangt de pijltjes af; zonder dit kom je er met de afstandsbediening niet meer uit.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                    else -> false
                }
            },
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (focused) Palette.surfaceFocused else Palette.surface)
                    .border(2.dp, if (focused) Palette.accent else Color.Transparent, RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    Icon(leading, null, tint = Palette.textMuted)
                    Spacer(Modifier.width(12.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = Palette.textMuted, fontSize = 20.sp)
                    inner()
                }
            }
        },
    )
}

/** Titel + horizontale rij; leeg = niets tonen. */
fun <T> LazyListScope.section(
    title: String,
    items: List<T>,
    key: (T) -> String,
    hint: String? = null,
    /** Unieke sleutel als dezelfde titel vaker voorkomt (bijv. "Kijk verder" bij series én films). */
    id: String = title,
    /** Rij binnen een groep (zie [groupHeader]): kleinere, grijze titel. */
    subRow: Boolean = false,
    /** Eigen scrollpositie, als de rij van buitenaf gescrold moet kunnen worden. */
    rowState: LazyListState? = null,
    /** Om de rij heen, bijv. om het scrollgedrag van de lijst eromheen terug te zetten (zie CategoryRowsTab). */
    rowWrapper: (@Composable (@Composable () -> Unit) -> Unit)? = null,
    /** De rij krijgt focus. */
    onRowFocused: (() -> Unit)? = null,
    content: @Composable (index: Int, item: T) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "title:$id") {
        Row(
            Modifier.padding(start = 56.dp, end = 56.dp, top = if (subRow) 12.dp else 28.dp, bottom = if (subRow) 6.dp else 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                title,
                style = if (subRow) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                color = if (subRow) Palette.textMuted else Palette.text,
            )
            if (hint != null) {
                Spacer(Modifier.width(16.dp))
                Text(hint, style = MaterialTheme.typography.bodySmall, color = Palette.textMuted, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
    item(key = "row:$id") {
        val ownState = rememberLazyListState()
        var focused by remember { mutableStateOf(false) }
        val row: @Composable () -> Unit = {
            LazyRow(
                state = rowState ?: ownState,
                modifier = if (onRowFocused == null) Modifier else Modifier.onFocusChanged {
                    if (it.hasFocus && !focused) onRowFocused()
                    focused = it.hasFocus
                },
                contentPadding = PaddingValues(horizontal = 56.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                itemsIndexed(items, key = { _, it -> key(it) }) { i, it -> content(i, it) }
            }
        }
        if (rowWrapper != null) rowWrapper(row) else row()
    }
}

/** Kop boven een groep rijen, bijv. "Series" met daaronder "Kijk verder" en "Nieuwe series". */
fun LazyListScope.groupHeader(title: String) {
    item(key = "group:$title") {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 56.dp, top = 32.dp, bottom = 4.dp),
        )
    }
}

@Composable
fun CenteredMessage(title: String, body: String? = null, content: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(10.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = Palette.textMuted, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        content()
    }
}
