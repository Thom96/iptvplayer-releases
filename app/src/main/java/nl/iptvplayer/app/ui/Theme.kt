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
 * Gallo-brandbook, palet Nachtwacht: petrolblauwe lagen als ondergrond, zilverwit voor tekst,
 * champagne (de gouden nekveren van een haan) voor focus, voortgang en sterren, en hanenkamrood
 * alleen voor LIVE en de kam in het logo. Diepte komt van lichtere lagen, niet van schaduwen.
 */
object Palette {
    // Oppervlakken, van donker naar licht.
    val rail = Color(0xFF07131A)
    val background = Color(0xFF0B1A22)
    val backgroundTop = Color(0xFF10212A)
    val surface = Color(0xFF14262F)
    val surfaceFocused = Color(0xFF1A2E38)
    val outline = Color(0x1FEDF3F2)

    // Tekst (contrast op background).
    val text = Color(0xFFEDF3F2) // zilverwit, 15,8:1
    val textSoft = Color(0xFFCFDCDD) // 12,6:1
    val textMuted = Color(0xFFA9BDC2) // 9,1:1
    val textDisabled = Color(0xFF8197A0) // 5,8:1

    // Accent en signalen.
    val accent = Color(0xFFE8CC94) // champagne, 11,4:1: focusrand, voortgang, sterren, de o van het logo
    val gold = accent
    val live = Color(0xFFBC3E2E) // hanenkamrood: alleen als vlak (wit erop 5,4:1), nooit als tekstkleur
    val comb = live
    val online = Color(0xFF8FD1A6) // gezien / verbonden
    val favorite = Color(0xFFE8907A)
}

val Outfit = FontFamily(
    Font(R.font.outfit_400, FontWeight.Normal),
    Font(R.font.outfit_500, FontWeight.Medium),
    Font(R.font.outfit_600, FontWeight.SemiBold),
    Font(R.font.outfit_700, FontWeight.Bold),
)

/** Koppen en het woordmerk. Young Serif heeft alleen een normaal gewicht. */
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

/** Duizendtallen met een punt, in het Engels met een komma. */
fun formatCount(n: Int): String = "%,d".format(n).let { if (I18n.language == AppLanguage.EN) it else it.replace(',', '.') }
