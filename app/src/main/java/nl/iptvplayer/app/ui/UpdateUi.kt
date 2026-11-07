package nl.iptvplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.UpdateState

/** Melding bij het opstarten als er een nieuwe versie is. */
@Composable
fun UpdatePrompt(vm: AppViewModel) {
    if (!vm.showUpdatePrompt) return
    val state = vm.update
    val info = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.NeedsPermission -> state.info
        else -> return
    }
    val updateButton = remember { FocusRequester() }
    Dialog(onDismissRequest = { vm.showUpdatePrompt = false }) {
        Column(
            Modifier
                .width(560.dp)
                .background(Palette.surface, RoundedCornerShape(24.dp))
                .padding(32.dp),
        ) {
            Text("Nieuwe versie beschikbaar", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Versie ${info.versionName} staat klaar. Je huidige versie is ${vm.updater.currentVersionName}.",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.textMuted,
            )
            info.notes?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(24.dp))
            UpdateStatusOrButtons(vm, state, updateButton, onLater = { vm.showUpdatePrompt = false })
        }
    }
    LaunchedEffect(Unit) { updateButton.requestSafely() }
}

/** Sectie "Updates" in Instellingen. */
@Composable
fun UpdateSection(vm: AppViewModel) {
    val state = vm.update
    Text(
        "Geïnstalleerde versie: ${vm.updater.currentVersionName}. De app kijkt bij elke start automatisch of er een nieuwe versie is.",
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.textMuted,
    )
    Spacer(Modifier.height(14.dp))
    // Eén vaste knop waarvan alleen de tekst en actie wisselen. Als de knop zou verdwijnen,
    // valt de focus terug op het tabblad Home en opent dat vanzelf.
    UpdateActionButton(vm, state)
    val message = when (state) {
        UpdateState.UpToDate -> "Je hebt de nieuwste versie."
        is UpdateState.Failed -> "Controleren mislukt: ${state.message}"
        is UpdateState.Available -> "Nieuwe versie beschikbaar: ${state.info.versionName}"
        is UpdateState.NeedsPermission ->
            "Sta eenmalig toe dat Gallo apps mag installeren (zet de schakelaar aan en ga terug). Daarna gaat het bijwerken vanzelf verder."
        else -> null
    }
    if (state is UpdateState.Downloading) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.width(420.dp)) { ProgressBar(state.progress) }
    }
    message?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.textMuted)
    }
}

/** De knop voor elke stap: controleren → bijwerken → downloaden → (toestemming) opnieuw proberen. */
@Composable
private fun UpdateActionButton(vm: AppViewModel, state: UpdateState, modifier: Modifier = Modifier) {
    val (label, action) = when (state) {
        UpdateState.Checking -> "Controleren…" to {}
        is UpdateState.Available -> "Nu bijwerken" to vm::installUpdate
        is UpdateState.Downloading -> "Downloaden… ${(state.progress * 100).toInt()}%" to {}
        is UpdateState.NeedsPermission -> "Opnieuw proberen" to vm::retryAfterPermission
        else -> "Controleren op updates" to { vm.checkForUpdates(manual = true) }
    }
    ActionButton(label, Icons.Outlined.SystemUpdate, onClick = action, modifier = modifier)
}

@Composable
private fun UpdateStatusOrButtons(vm: AppViewModel, state: UpdateState, focus: FocusRequester?, onLater: (() -> Unit)?) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        UpdateActionButton(vm, state, modifier = if (focus != null) Modifier.focusRequester(focus) else Modifier)
        if (onLater != null && state !is UpdateState.Downloading) ActionButton("Later", null, onClick = onLater)
    }
    if (state is UpdateState.Downloading) {
        Spacer(Modifier.height(12.dp))
        ProgressBar(state.progress)
    }
}
