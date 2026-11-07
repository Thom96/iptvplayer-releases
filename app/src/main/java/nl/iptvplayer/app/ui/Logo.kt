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
 * Woordmerk uit het brandbook: "Gall" in Young Serif, gevolgd door de o (champagne afspeelknop met
 * een rode hanenkam). De onderkant van de cirkel staat op de basislijn van de letters.
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
                .padding(start = em * 0.027f)
                .size(width = em * 0.598f, height = em * 0.741f)
                // De cirkel eindigt 2/102 boven de onderkant van de afbeelding: die lijn op de basislijn.
                .alignBy { it.measuredHeight * 100 / 102 },
        )
    }
}

/** Woordmerk met "PLAYER" eronder, voor het inlogscherm. */
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
