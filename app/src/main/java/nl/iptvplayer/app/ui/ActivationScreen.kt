package nl.iptvplayer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.Gate
import nl.iptvplayer.app.data.License
import nl.iptvplayer.app.data.LicenseStatus
import nl.iptvplayer.app.i18n.AppLanguage
import nl.iptvplayer.app.i18n.I18n
import nl.iptvplayer.app.i18n.S

/**
 * Activation screen (design: "Gallo — activatie en admin", TV screen).
 * [Gate.TRIAL_NOTICE]: briefly at startup during the trial, with "Continue with trial".
 * [Gate.LOCKED]: trial or subscription over, blocked or no contact for too long; checks every 15 s whether payment came in.
 */
@Composable
fun ActivationScreen(vm: AppViewModel, gate: Gate) {
    if (gate == Gate.LOCKED) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(if (vm.licenseError) 30_000 else 15_000)
                vm.checkLicense()
            }
        }
    } else {
        BackHandler { vm.trialNoticeSeen = true }
    }
    ActivationContent(
        license = vm.license,
        gate = gate,
        checking = vm.licenseChecking,
        onContinue = { vm.trialNoticeSeen = true },
        onCheck = vm::checkLicense,
        onPickLanguage = vm::setAppLanguage,
    )
}

/** The screen itself, separate from the ViewModel (so it can also be drawn in tests). */
@Composable
fun ActivationContent(
    license: License?,
    gate: Gate,
    checking: Boolean,
    onContinue: () -> Unit,
    onCheck: () -> Unit,
    onPickLanguage: (AppLanguage) -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    val locked = gate == Gate.LOCKED
    val blocked = license?.status == LicenseStatus.BLOCKED
    // No contact for too long, or never: then the problem is the connection, not the payment.
    val offline = license == null || !license.isFresh(now)
    val primary = remember { FocusRequester() }
    val globe = remember { FocusRequester() }
    var languageMenuOpen by remember { mutableStateOf(false) }
    var focusReturnsToGlobe by remember { mutableStateOf(false) }

    LaunchedEffect(gate) { primary.requestSafely() }

    val title = when {
        !locked -> S.activateTitle
        offline && license == null && checking -> S.connecting
        offline -> S.galloOffline
        blocked -> S.deviceBlocked
        license?.plan != null -> S.subscriptionEnded
        else -> S.trialEnded
    }
    val showSteps = license != null && !blocked && !offline

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GalloWordmark(30.sp)
            Spacer(Modifier.weight(1f))
            StatusPill(license, now, locked)
            Spacer(Modifier.width(12.dp))
            GlobeButton(onClick = { languageMenuOpen = true; focusReturnsToGlobe = true }, modifier = Modifier.focusRequester(globe))
        }

        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium)

                if (showSteps && license != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Step(1, S.activateScan(shortUrl(license.activateUrl)))
                        Step(2, S.activateEnterCode)
                        Step(3, S.activatePay)
                    }
                } else if (offline && !(license == null && checking)) {
                    Text(S.galloOfflineHint, style = MaterialTheme.typography.bodyLarge, color = Palette.textSoft)
                } else if (blocked) {
                    Text(S.deviceBlockedHint, style = MaterialTheme.typography.bodyLarge, color = Palette.textSoft)
                }

                if (license != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(S.deviceCode.uppercase(), style = MaterialTheme.typography.labelMedium, color = Palette.textMuted, letterSpacing = 0.12.em)
                        Text(
                            license.code,
                            color = Palette.accent,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.14.em,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!locked) {
                        ActionButton(S.continueTrial, Icons.Filled.PlayArrow, onContinue, Modifier.focusRequester(primary), primary = true)
                        ActionButton(S.checkStatus, Icons.Filled.Refresh, onCheck)
                    } else {
                        ActionButton(S.checkStatus, Icons.Filled.Refresh, onCheck, Modifier.focusRequester(primary), primary = true)
                    }
                }
            }

            if (showSteps && license != null) {
                Spacer(Modifier.width(48.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    QrCode(license.activateUrl, Modifier.size(200.dp))
                    if (locked) Text(S.waitingForPayment, style = MaterialTheme.typography.bodySmall, color = Palette.textMuted)
                }
            }
        }
    }

    if (languageMenuOpen) {
        LanguageMenu(
            onPick = { language ->
                languageMenuOpen = false
                onPickLanguage(language)
            },
            onDismiss = { languageMenuOpen = false },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 92.dp, end = 56.dp),
        )
    } else {
        // Back on the globe after closing the menu.
        LaunchedEffect(Unit) { if (focusReturnsToGlobe) globe.requestSafely() }
    }
    }
}

/** Round globe button with the current language code, top right. Opens [LanguageMenu]. */
@Composable
private fun GlobeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = S.appLanguage
    Surface(
        onClick = onClick,
        modifier = modifier.tapToClick(onClick).semantics { contentDescription = label },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.surface,
            contentColor = Palette.textSoft,
            focusedContainerColor = Palette.surfaceFocused,
            focusedContentColor = Palette.text,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Palette.accent), shape = RoundedCornerShape(50)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(I18n.language.code.uppercase(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The five app languages, each in its own language. Focus stays inside; Back closes it. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LanguageMenu(onPick: (AppLanguage) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val current = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) { current.requestSafely() }
    Column(
        modifier
            .width(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.surface)
            .padding(8.dp)
            .focusGroup()
            .focusProperties { exit = { FocusRequester.Cancel } },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppLanguage.entries.forEach { language ->
            val selected = language == I18n.language
            ChipButton(
                text = language.nativeName,
                selected = selected,
                onClick = { onPick(language) },
                fill = true,
                modifier = if (selected) Modifier.focusRequester(current) else Modifier,
            )
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("$number", color = Palette.accent, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(14.dp))
        Text(text, color = Palette.textSoft, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StatusPill(license: License?, now: Long, locked: Boolean) {
    val text = when {
        license == null -> return
        license.status == LicenseStatus.BLOCKED -> S.licenseBlocked
        locked && license.allowsUse(now) -> return // only no contact for too long; the status itself is fine
        locked -> S.licenseExpired
        license.status == LicenseStatus.TRIAL -> S.trialDaysLeft(license.daysLeft(now) ?: 0)
        else -> return
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Palette.surface).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (locked) Palette.textMuted else Palette.accent))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.textSoft)
    }
}

/** "https://gallo-heermoes-13.vercel.app/activeren?code=…" → "gallo-heermoes-13.vercel.app/activeren". */
private fun shortUrl(url: String): String = url.substringAfter("://").substringBefore("?")

@Composable
private fun QrCode(content: String, modifier: Modifier = Modifier) {
    val matrix: BitMatrix? = remember(content) {
        runCatching {
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
        }.getOrNull()
    }
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(Palette.text).padding(14.dp)
            .semantics { contentDescription = shortUrl(content) },
    ) {
        if (matrix == null) return@Box
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.minDimension / matrix.width
            for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    // Overlapping a little prevents thin lines between the squares.
                    drawRect(QR_DARK, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}

private val QR_DARK = Color(0xFF0B1A22)
