package nl.iptvplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import nl.iptvplayer.app.R

/** Google TV-achtig: neutraal donkergrijs, wit voor focus en één rustige accentkleur. */
object Palette {
    val background = Color(0xFF111214)
    val backgroundTop = Color(0xFF1B1D21)
    val surface = Color(0xFF1F2125)
    val surfaceFocused = Color(0xFF30333A)
    val outline = Color(0x1FFFFFFF)
    val accent = Color(0xFF8AB4F8)
    val text = Color(0xFFE8EAED)
    val textMuted = Color(0xFF9AA0A6)
    val gold = Color(0xFFFDD663)
    val online = Color(0xFF81C995)

    // Kleuren uit het logo (tv-icoon): rood, geel, crème en donkerblauw.
    val brandRed = Color(0xFFE63B2E)
    val brandYellow = Color(0xFFFFC93C)
    val brandCream = Color(0xFFFFF3E0)
    val brandNavy = Color(0xFF1E1B2E)
}

val Outfit = FontFamily(
    Font(R.font.outfit_400, FontWeight.Normal),
    Font(R.font.outfit_500, FontWeight.Medium),
    Font(R.font.outfit_600, FontWeight.SemiBold),
    Font(R.font.outfit_700, FontWeight.Bold),
)

private fun TextStyle.outfit(weight: FontWeight) = copy(fontFamily = Outfit, fontWeight = weight)

private val AppTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.outfit(FontWeight.Medium),
        displayMedium = t.displayMedium.outfit(FontWeight.Medium),
        displaySmall = t.displaySmall.outfit(FontWeight.Medium),
        headlineLarge = t.headlineLarge.outfit(FontWeight.Normal),
        headlineMedium = t.headlineMedium.outfit(FontWeight.Normal),
        headlineSmall = t.headlineSmall.outfit(FontWeight.Normal),
        titleLarge = t.titleLarge.outfit(FontWeight.Normal),
        titleMedium = t.titleMedium.outfit(FontWeight.Medium),
        titleSmall = t.titleSmall.outfit(FontWeight.Medium),
        bodyLarge = t.bodyLarge.outfit(FontWeight.Normal),
        bodyMedium = t.bodyMedium.outfit(FontWeight.Normal),
        bodySmall = t.bodySmall.outfit(FontWeight.Normal),
        labelLarge = t.labelLarge.outfit(FontWeight.Medium),
        labelMedium = t.labelMedium.outfit(FontWeight.Medium),
        labelSmall = t.labelSmall.outfit(FontWeight.Medium),
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.accent,
            onPrimary = Color.Black,
            background = Palette.background,
            onBackground = Palette.text,
            surface = Palette.surface,
            onSurface = Palette.text,
            surfaceVariant = Palette.surfaceFocused,
            onSurfaceVariant = Palette.textMuted,
        ),
        typography = AppTypography,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            colors = SurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = Palette.text),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0f to Palette.backgroundTop, 0.45f to Palette.background)),
            )
            content()
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms < 0) return "--:--"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatDuration(secs: Int): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    return if (h > 0) "${h}u ${m}m" else "${m} min"
}

fun formatRemaining(p: nl.iptvplayer.app.data.Progress): String {
    val mins = ((p.durationMs - p.positionMs).coerceAtLeast(0) / 60_000).toInt()
    return if (mins >= 60) "${mins / 60}u ${mins % 60}m" else "$mins min"
}

fun formatCount(n: Int): String = "%,d".format(n).replace(',', '.')
