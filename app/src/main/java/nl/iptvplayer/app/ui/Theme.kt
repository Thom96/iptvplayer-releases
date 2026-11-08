package nl.iptvplayer.app.ui

import nl.iptvplayer.app.i18n.S
import nl.iptvplayer.app.i18n.I18n
import nl.iptvplayer.app.i18n.AppLanguage
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

/**
 * Gallo brandbook, palette Night Watch: petrol blue layers as the background, silver white for text,
 * champagne (a rooster's golden hackles) for focus, progress and stars, and comb red
 * only for LIVE and the comb in the logo. Depth comes from lighter layers, not from shadows.
 */
object Palette {
    // Surfaces, from dark to light.
    val rail = Color(0xFF07131A)
    val background = Color(0xFF0B1A22)
    val backgroundTop = Color(0xFF10212A)
    val surface = Color(0xFF14262F)
    val surfaceFocused = Color(0xFF1A2E38)
    val outline = Color(0x1FEDF3F2)

    // Text (contrast on background).
    val text = Color(0xFFEDF3F2) // silver white, 15.8:1
    val textSoft = Color(0xFFCFDCDD) // 12.6:1
    val textMuted = Color(0xFFA9BDC2) // 9.1:1
    val textDisabled = Color(0xFF8197A0) // 5.8:1

    // Accent and signals.
    val accent = Color(0xFFE8CC94) // champagne, 11.4:1: focus border, progress, stars, the o of the logo
    val gold = accent
    val live = Color(0xFFBC3E2E) // comb red: only as a surface (white on it 5.4:1), never as a text colour
    val comb = live
    val online = Color(0xFF8FD1A6) // watched / connected
    val favorite = Color(0xFFE8907A)

    // Over video and images: dark petrol instead of black, silver white text on top.
    val video = Color(0xFF000000) // behind the picture itself (black bars stay black)
    val scrim = Color(0xE607131A) // gradient under text over the picture
    val scrimLight = Color(0x9907131A) // behind a side panel
    val badge = Color(0xCC07131A) // small label on a poster or in the seek bar
    val glass = Color(0x33EDF3F2) // button over the picture without focus
    val track = Color(0x40EDF3F2) // background of a seek bar
    val trackBuffered = Color(0x66EDF3F2) // already loaded part
}

val Outfit = FontFamily(
    Font(R.font.outfit_400, FontWeight.Normal),
    Font(R.font.outfit_500, FontWeight.Medium),
    Font(R.font.outfit_600, FontWeight.SemiBold),
    Font(R.font.outfit_700, FontWeight.Bold),
)

/** Headings and the wordmark. Young Serif only has a regular weight. */
val YoungSerif = FontFamily(Font(R.font.young_serif_400, FontWeight.Normal))

private fun TextStyle.outfit(weight: FontWeight) = copy(fontFamily = Outfit, fontWeight = weight)
private fun TextStyle.serif() = copy(fontFamily = YoungSerif, fontWeight = FontWeight.Normal)

private val AppTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.serif(),
        displayMedium = t.displayMedium.serif(),
        displaySmall = t.displaySmall.serif(),
        headlineLarge = t.headlineLarge.serif(),
        headlineMedium = t.headlineMedium.serif(),
        headlineSmall = t.headlineSmall.serif(),
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
    return if (h > 0) S.hoursMinutes(h, m) else S.minutes(m)
}

fun formatRemaining(p: nl.iptvplayer.app.data.Progress): String {
    val mins = ((p.durationMs - p.positionMs).coerceAtLeast(0) / 60_000).toInt()
    return if (mins >= 60) S.hoursMinutes(mins / 60, mins % 60) else S.minutes(mins)
}

/** Thousands with a dot, in English with a comma. */
fun formatCount(n: Int): String = "%,d".format(n).let { if (I18n.language == AppLanguage.EN) it else it.replace(',', '.') }
