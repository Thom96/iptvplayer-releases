package nl.iptvplayer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.i18n.AppLanguage
import nl.iptvplayer.app.i18n.I18n

/** Eerste start: taal kiezen. De kop staat steeds in de taal waar de focus op staat. */
@Composable
fun LanguageScreen(vm: AppViewModel) {
    var focused by remember { mutableStateOf(I18n.language) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestSafely() }

    Row(Modifier.fillMaxSize().padding(horizontal = 96.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            GalloLogo(size = 72.sp)
            Spacer(Modifier.height(20.dp))
            Text(focused.strings.chooseLanguage, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            // Twee regels vrijhouden, zodat het logo niet verspringt als een vertaling langer is.
            Text(focused.strings.chooseLanguageHint, style = MaterialTheme.typography.bodyLarge, color = Palette.textMuted, minLines = 2)
        }
        Spacer(Modifier.width(64.dp))
        LanguageList(
            selected = I18n.language,
            onSelect = vm::setAppLanguage,
            onFocus = { focused = it },
            modifier = Modifier.weight(1f),
            firstFocus = first,
        )
    }
}

/** Alle talen onder elkaar, in hun eigen taal. */
@Composable
private fun LanguageList(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (AppLanguage) -> Unit = {},
    firstFocus: FocusRequester? = null,
) {
    Column(modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppLanguage.entries.forEach { lang ->
            ChipButton(
                text = lang.nativeName,
                selected = lang == selected,
                onClick = { onSelect(lang) },
                fill = true,
                modifier = Modifier
                    .width(360.dp)
                    .onFocusChanged { if (it.isFocused) onFocus(lang) }
                    .then(if (firstFocus != null && lang == selected) Modifier.focusRequester(firstFocus) else Modifier),
            )
        }
    }
}
