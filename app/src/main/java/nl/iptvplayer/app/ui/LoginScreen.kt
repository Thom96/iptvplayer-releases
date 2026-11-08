package nl.iptvplayer.app.ui

import nl.iptvplayer.app.i18n.S
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import nl.iptvplayer.app.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.iptvplayer.app.AppState
import nl.iptvplayer.app.AppViewModel

@Composable
fun LoginScreen(vm: AppViewModel, state: AppState.Login) {
    var server by rememberSaveable { mutableStateOf(vm.storage.lastServer) }
    var username by rememberSaveable { mutableStateOf(vm.storage.lastUsername) }
    var password by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val first = remember { FocusRequester() }
    val loginButton = remember { FocusRequester() }

    val submit = {
        if (server.isNotBlank() && username.isNotBlank() && !state.busy) vm.login(server, username, password)
    }

    LaunchedEffect(Unit) {
        if (server.isNotEmpty() && username.isNotEmpty()) loginButton.requestSafely() else first.requestSafely()
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 96.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            GalloLogo(size = 72.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                S.loginIntro,
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.textMuted,
            )
        }
        Spacer(Modifier.width(64.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TvTextField(
                value = server,
                onValueChange = { server = it },
                placeholder = S.serverPlaceholder,
                keyboardType = KeyboardType.Uri,
                onImeAction = { focus.moveFocus(FocusDirection.Down) },
                modifier = Modifier.fillMaxWidth().focusRequester(first),
            )
            TvTextField(
                value = username,
                onValueChange = { username = it },
                placeholder = S.username,
                onImeAction = { focus.moveFocus(FocusDirection.Down) },
                modifier = Modifier.fillMaxWidth(),
            )
            TvTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = S.password,
                password = true,
                imeAction = ImeAction.Done,
                onImeAction = submit,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            ActionButton(
                text = if (state.busy) S.loggingIn else S.login,
                icon = if (state.busy) Icons.Filled.Refresh else Icons.AutoMirrored.Filled.Login,
                onClick = submit,
                modifier = Modifier.focusRequester(loginButton),
            )
        }
    }
}

/**
 * Loading screen. When downloading the lists ([AppState.Loading.quick] = false) the rooster's grain game
 * with explanation; on a quick start from the saved lists only the logo and a rooster that crows once.
 */
@Composable
fun LoadingScreen(state: AppState.Loading, onRoosterDone: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.quick) {
            GalloLogo(64.sp)
            Spacer(Modifier.height(28.dp))
            LoadingRooster(state.progress, quick = true, finished = state.finished, onFinished = onRoosterDone)
        } else {
            LoadingRooster(state.progress, finished = state.finished, onFinished = onRoosterDone)
            Spacer(Modifier.height(16.dp))
            Text(state.message, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                S.firstLoadHint,
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun FailedScreen(message: String, onRetry: () -> Unit, onLogout: () -> Unit) {
    val retry = remember { FocusRequester() }
    LaunchedEffect(Unit) { retry.requestSafely() }
    CenteredMessage(S.cannotConnect, message) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ActionButton(S.retry, Icons.Filled.Refresh, onRetry, Modifier.focusRequester(retry))
            ActionButton(S.otherLogin, Icons.AutoMirrored.Filled.Login, onLogout)
        }
    }
}
