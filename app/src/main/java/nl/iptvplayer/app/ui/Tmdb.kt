package nl.iptvplayer.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.withTimeoutOrNull
import nl.iptvplayer.app.App
import nl.iptvplayer.app.data.TmdbMeta
import nl.iptvplayer.app.data.XtreamClient
import nl.iptvplayer.app.i18n.I18n
import nl.iptvplayer.app.i18n.S

/** How long a detail screen waits for TMDB before it shows the provider's data alone. */
private const val TMDB_WAIT_MS = 3_000L

/** TMDB details for a movie or series, or null (demo mode, no key on the server, not found, too slow). */
suspend fun fetchTmdb(context: Context, client: XtreamClient?, series: Boolean, tmdbId: Int?, title: String, year: Int?): TmdbMeta? {
    if (client == null || client.demo) return null
    val metadata = (context.applicationContext as App).metadata
    val language = I18n.language.code
    return withTimeoutOrNull(TMDB_WAIT_MS) {
        if (series) metadata.series(tmdbId, title, year, language) else metadata.movie(tmdbId, title, year, language)
    }
}

/** TMDB's colours: dark blue text on their green-to-blue gradient. */
private val TMDB_GRADIENT = Brush.horizontalGradient(listOf(Color(0xFF90CEA1), Color(0xFF01B4E4)))
private val TMDB_BLUE = Color(0xFF0D253F)

/** Attribution required by TMDB's terms, shown wherever their data is used. */
@Composable
fun TmdbBadge(modifier: Modifier = Modifier, text: String = S.tmdbSource) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "TMDB",
            color = TMDB_BLUE,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(TMDB_GRADIENT).padding(horizontal = 6.dp, vertical = 1.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.textMuted)
    }
}
