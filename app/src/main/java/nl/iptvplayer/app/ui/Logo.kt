package nl.iptvplayer.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import nl.iptvplayer.app.R

/**
 * Wordmark from the brandbook: "Gall" in Young Serif, followed by the o: a small retro TV with a
 * rooster head on the screen. The bottom of the TV sits on the baseline of the letters.
 */
@Composable
fun GalloWordmark(size: TextUnit, modifier: Modifier = Modifier) {
    val em = with(LocalDensity.current) { size.toDp() }
    Row(modifier.clearAndSetSemantics { contentDescription = "Gallo" }) {
        Text(
            "Gall",
            style = TextStyle(fontFamily = YoungSerif, fontSize = size, color = Palette.text),
            modifier = Modifier.alignByBaseline(),
        )
        Image(
            painterResource(R.drawable.gallo_o),
            contentDescription = null,
            modifier = Modifier
                .padding(start = em * 0.036f)
                .size(width = em * 0.84f, height = em * 0.77f)
                // The TV ends 1/92 above the bottom of the image: that line goes on the baseline.
                .alignBy { it.measuredHeight * 91 / 92 },
        )
    }
}

/** Wordmark with "PLAYER" below it, for the login screen. */
@Composable
fun GalloLogo(size: TextUnit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GalloWordmark(size)
        Text(
            "PLAYER",
            style = TextStyle(
                fontFamily = Outfit,
                fontWeight = FontWeight.SemiBold,
                fontSize = size * 0.14f,
                letterSpacing = 0.5.em,
                color = Palette.accent,
            ),
        )
    }
}
