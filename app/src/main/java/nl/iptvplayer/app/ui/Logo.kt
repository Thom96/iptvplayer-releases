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
 * GalloTV wordmark from the brandbook: "Gallo" in Young Serif, followed by a small retro TV with a
 * rooster head on the screen (Gallo + TV). The bottom of the TV sits on the baseline of the letters.
 */
@Composable
fun GalloWordmark(size: TextUnit, modifier: Modifier = Modifier) {
    val em = with(LocalDensity.current) { size.toDp() }
    Row(modifier.clearAndSetSemantics { contentDescription = "GalloTV" }) {
        Text(
            "Gallo",
            style = TextStyle(fontFamily = YoungSerif, fontSize = size, color = Palette.text),
            modifier = Modifier.alignByBaseline(),
        )
        Image(
            painterResource(R.drawable.gallo_o),
            contentDescription = null,
            modifier = Modifier
                .padding(start = em * 0.12f)
                .size(width = em * 0.84f, height = em * 0.77f)
                // The TV ends 1/92 above the bottom of the image: that line goes on the baseline.
                .alignBy { it.measuredHeight * 91 / 92 },
        )
    }
}

/** Large wordmark for the login and language screens. The TV says "TV", so no extra subtitle. */
@Composable
fun GalloLogo(size: TextUnit, modifier: Modifier = Modifier) {
    GalloWordmark(size, modifier)
}
