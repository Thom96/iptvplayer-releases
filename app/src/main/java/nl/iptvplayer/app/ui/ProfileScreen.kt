package nl.iptvplayer.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.People
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.iptvplayer.app.AppViewModel
import nl.iptvplayer.app.ProfileMode
import nl.iptvplayer.app.data.Kind
import nl.iptvplayer.app.data.ParentalFilter
import nl.iptvplayer.app.data.Profile
import nl.iptvplayer.app.i18n.S

/** Avatar colours, picked to stay readable with dark text on the petrol background. */
private val AvatarColors = listOf(
    Color(0xFF8FD1A6), Color(0xFFE8CC94), Color(0xFFE8907A), Color(0xFF8EC5E8), Color(0xFFC9A8E8), Color(0xFFEDF3F2),
)

private fun avatarColor(profile: Profile) = AvatarColors[profile.color.mod(AvatarColors.size)]

/** Round avatar with the first letter of the name. */
@Composable
fun ProfileAvatar(profile: Profile, name: String, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(avatarColor(profile), CircleShape), contentAlignment = Alignment.Center) {
        Text(
            name.trim().take(1).uppercase(),
            color = Palette.background,
            fontSize = (size.value * 0.42f).sp,
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

/**
 * "Who's watching?" at start (with two or more profiles), and managing profiles: name, colour, kids profile,
 * PIN lock and what a kids profile can see. Changing anything asks for the PIN when one is set.
 */
@Composable
fun ProfileScreen(vm: AppViewModel, mode: ProfileMode) {
    val activity = LocalContext.current as? Activity
    var managing by remember { mutableStateOf(mode == ProfileMode.MANAGE) }
    var draft by remember { mutableStateOf<Profile?>(null) }
    var parental by remember { mutableStateOf(false) }
    // Something that waits for the PIN: opening a locked profile, or managing.
    var pinThen by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun withManagePin(action: () -> Unit) {
        if (vm.settingsLocked) pinThen = { vm.settingsUnlocked = true; action() } else action()
    }

    fun newProfile() = Profile(
        id = vm.newProfileId(),
        name = "",
        color = vm.profiles.size % AvatarColors.size,
        // Once there is a kids profile, new grown-up profiles are locked as well.
        locked = vm.profiles.any { it.kid },
    )

    BackHandler {
        when {
            parental -> parental = false
            draft != null -> draft = null
            managing && mode == ProfileMode.PICK -> managing = false
            // At start, Back continues in the last profile, unless that one is locked.
            !vm.profileChosen && vm.needsPin(vm.activeProfile) -> activity?.finish()
            else -> vm.profileScreen = null
        }
    }

    val d = draft
    when {
        d != null && parental -> ParentalPane(vm, d, onChange = { draft = it }, onBack = { parental = false })
        d != null -> ProfileEditor(
            vm,
            d,
            onChange = { draft = it },
            onParental = { parental = true },
            onSave = { vm.saveProfile(it); draft = null },
            onDelete = { vm.deleteProfile(d.id); draft = null },
            onCancel = { draft = null },
        )
        else -> ProfileGrid(
            vm,
            managing = managing,
            onPick = { p ->
                if (managing) {
                    draft = p
                } else if (vm.needsPin(p) && !(p.id == vm.activeProfile.id && vm.profileChosen)) {
                    pinThen = { vm.selectProfile(p) }
                } else {
                    vm.selectProfile(p)
                }
            },
            onAdd = { withManagePin { managing = true; draft = newProfile() } },
            onManage = {
                if (managing) {
                    if (mode == ProfileMode.MANAGE) vm.profileScreen = null else managing = false
                } else {
                    withManagePin { managing = true }
                }
            },
        )
    }

    pinThen?.let { action ->
        PinDialog(vm, create = false, onSuccess = { pinThen = null; action() }, onDismiss = { pinThen = null })
    }
}

@Composable
private fun ProfileGrid(
    vm: AppViewModel,
    managing: Boolean,
    onPick: (Profile) -> Unit,
    onAdd: () -> Unit,
    onManage: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(managing) { first.requestSafely() }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(if (managing) S.manageProfiles else S.whoIsWatching, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(40.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Top) {
            vm.profiles.forEach { p ->
                val start = if (managing) p == vm.profiles.first() else p.id == vm.activeProfile.id
                ProfileTile(
                    label = vm.profileName(p),
                    badge = if (p.kid) S.kidBadge else null,
                    lockedIcon = !managing && vm.needsPin(p),
                    editIcon = managing,
                    onClick = { onPick(p) },
                    modifier = if (start) Modifier.focusRequester(first) else Modifier,
                ) { ProfileAvatar(p, vm.profileName(p), 112.dp) }
            }
            if (vm.profiles.size < Profile.MAX) {
                ProfileTile(label = S.addProfile, onClick = onAdd) {
                    Box(Modifier.size(112.dp).background(Palette.surfaceFocused, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(44.dp), tint = Palette.textMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(44.dp))
        ActionButton(
            if (managing) S.done else S.manageProfiles,
            if (managing) null else Icons.Outlined.Edit,
            onClick = onManage,
        )
    }
}

/** Avatar with the name below it; focus = champagne ring around the circle. */
@Composable
private fun ProfileTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    lockedIcon: Boolean = false,
    editIcon: Boolean = false,
    avatar: @Composable () -> Unit,
) {
    Column(Modifier.width(128.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(128.dp).semantics { contentDescription = label }.tapToClick(onClick),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Palette.accent), shape = CircleShape)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                avatar()
                if (editIcon || lockedIcon) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp).size(34.dp).background(Palette.rail, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (editIcon) Icons.Outlined.Edit else Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = Palette.text)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (badge != null) Text(badge, style = MaterialTheme.typography.labelMedium, color = Palette.accent)
    }
}

@Composable
private fun ProfileEditor(
    vm: AppViewModel,
    profile: Profile,
    onChange: (Profile) -> Unit,
    onParental: () -> Unit,
    onSave: (Profile) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val isNew = vm.profiles.none { it.id == profile.id }
    var createPin by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val nameField = remember { FocusRequester() }
    LaunchedEffect(Unit) { nameField.requestSafely() }

    val shownName = profile.name.ifBlank { if (profile.id == Profile.MAIN) vm.profileName(profile) else "?" }
    Row(Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        ProfileAvatar(profile, shownName, 160.dp)
        Spacer(Modifier.width(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (isNew) S.newProfile else S.editProfile, style = MaterialTheme.typography.headlineMedium)
            TvTextField(
                value = profile.name,
                onValueChange = { onChange(profile.copy(name = it.take(20))) },
                placeholder = if (profile.id == Profile.MAIN) vm.profileName(profile) else S.profileNameHint,
                imeAction = ImeAction.Done,
                modifier = Modifier.width(420.dp).focusRequester(nameField),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(S.profileColor, color = Palette.textMuted, modifier = Modifier.width(80.dp))
                AvatarColors.indices.forEach { i ->
                    ColorSwatch(AvatarColors[i], selected = profile.color.mod(AvatarColors.size) == i, onClick = { onChange(profile.copy(color = i)) })
                }
            }
            ToggleLine(S.kidProfile, S.kidProfileHint, profile.kid) {
                when {
                    profile.kid -> onChange(profile.copy(kid = false))
                    !vm.hasPin -> createPin = true
                    else -> onChange(profile.copy(kid = true, locked = false))
                }
            }
            if (profile.kid) {
                ActionButton(S.allowedContent, Icons.Outlined.People, onClick = onParental)
            } else {
                ToggleLine(S.lockProfile, S.lockProfileHint, profile.locked) { onChange(profile.copy(locked = !profile.locked)) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ActionButton(S.save, null, primary = true, onClick = {
                    if (profile.name.isBlank() && profile.id != Profile.MAIN) nameField.requestFocus() else onSave(profile.copy(name = profile.name.trim()))
                })
                ActionButton(S.cancel, null, onClick = onCancel)
                if (!isNew && profile.id != Profile.MAIN) {
                    ActionButton(S.deleteProfile, Icons.Outlined.Delete, onClick = { confirmDelete = true })
                }
            }
        }
    }

    if (createPin) {
        PinDialog(
            vm,
            create = true,
            hint = S.newPinKidHint,
            onSuccess = { createPin = false; onChange(profile.copy(kid = true, locked = false)) },
            onDismiss = { createPin = false },
        )
    }
    if (confirmDelete) {
        ConfirmRemoval(
            title = S.deleteProfileWarning(shownName),
            question = S.deleteProfileQuestion,
            confirmLabel = S.deleteProfile,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp).tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = color, focusedContainerColor = color),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(3.dp, Palette.text), shape = CircleShape) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, Palette.accent), shape = CircleShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {}
}

/** A setting that is on or off, with its explanation next to it. */
@Composable
private fun ToggleLine(title: String, hint: String, on: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChipButton(text = title, selected = on, onClick = onToggle, trailing = if (on) "✓" else null, modifier = Modifier.width(240.dp), fill = true)
        Spacer(Modifier.width(16.dp))
        Text(hint, style = MaterialTheme.typography.bodySmall, color = Palette.textMuted, modifier = Modifier.width(380.dp))
    }
}

/** Which categories a kids profile can see, per kind; adult-looking ones start hidden. */
@Composable
private fun ParentalPane(vm: AppViewModel, profile: Profile, onChange: (Profile) -> Unit, onBack: () -> Unit) {
    var kind by remember { mutableStateOf(Kind.MOVIE) }
    val library = vm.fullLibrary
    val cats = library?.categories(kind).orEmpty()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestSafely() }

    fun toggle(catId: String, name: String, hidden: Boolean) {
        val key = ParentalFilter.categoryKey(kind, catId)
        onChange(
            if (hidden) {
                profile.copy(hidden = profile.hidden - key, shown = if (ParentalFilter.looksAdult(name)) profile.shown + key else profile.shown)
            } else {
                profile.copy(hidden = profile.hidden + key, shown = profile.shown - key)
            },
        )
    }

    Column(Modifier.fillMaxSize().padding(start = 72.dp, end = 72.dp, top = 36.dp)) {
        Text(S.parentalTitle(profile.name.ifBlank { vm.profileName(profile) }), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(S.parentalHint, style = MaterialTheme.typography.bodyMedium, color = Palette.textMuted)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(Kind.MOVIE to S.tabMovies, Kind.SERIES to S.tabSeries, Kind.LIVE to S.tabLive).forEach { (k, label) ->
                ChipButton(label, selected = kind == k, onClick = { kind = k }, modifier = if (k == Kind.MOVIE) Modifier.focusRequester(first) else Modifier)
            }
            Spacer(Modifier.width(24.dp))
            ActionButton(S.hideAll, null, onClick = {
                val keys = cats.map { ParentalFilter.categoryKey(kind, it.id) }.toSet()
                onChange(profile.copy(hidden = profile.hidden + keys, shown = profile.shown - keys))
            })
            // "Show all" leaves adult-looking categories hidden; those can only be allowed one by one.
            ActionButton(S.showAll, null, onClick = {
                val keys = cats.map { ParentalFilter.categoryKey(kind, it.id) }.toSet()
                onChange(profile.copy(hidden = profile.hidden - keys))
            })
            Spacer(Modifier.weight(1f))
            ActionButton(S.done, null, primary = true, onClick = onBack)
        }
        Spacer(Modifier.height(16.dp))
        if (cats.isEmpty()) {
            Text(S.noCategories, color = Palette.textMuted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                items(cats, key = { it.id }) { cat ->
                    val hidden = ParentalFilter.isHidden(profile, kind, cat)
                    ChipButton(
                        text = cat.name,
                        selected = !hidden,
                        onClick = { toggle(cat.id, cat.name, hidden) },
                        trailing = if (hidden) S.hiddenLabel else S.visibleLabel,
                        fill = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Four-digit PIN with a number pad (the remote's digit keys also work).
 * [create]: choose a new PIN (twice); otherwise check it, with "Forgot PIN?" via the subscription password.
 */
@Composable
fun PinDialog(
    vm: AppViewModel,
    create: Boolean,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
    hint: String? = null,
) {
    var creating by remember { mutableStateOf(create) }
    var forgot by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }

    fun complete(pin: String) {
        entered = ""
        when {
            !creating -> if (vm.checkPin(pin)) onSuccess() else error = S.wrongPin
            firstPin == null -> { firstPin = pin; error = null }
            firstPin == pin -> { vm.setPin(pin); onSuccess() }
            else -> { firstPin = null; error = S.pinsDontMatch }
        }
    }

    fun type(digit: Char) {
        if (entered.length >= 4) return
        entered += digit
        if (entered.length == 4) complete(entered)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(460.dp)
                .background(Palette.surface, RoundedCornerShape(24.dp))
                .padding(28.dp)
                .onPreviewKeyEvent { e ->
                    if (forgot || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val digit = digitOf(e.key) ?: return@onPreviewKeyEvent false
                    type(digit)
                    true
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (forgot) {
                ForgotPin(
                    password = password,
                    onPassword = { password = it },
                    error = error,
                    onNext = {
                        if (vm.checkAccountPassword(password)) {
                            forgot = false; creating = true; firstPin = null; error = null
                        } else {
                            error = S.wrongPassword
                        }
                    },
                )
                return@Column
            }
            val title = when {
                !creating -> S.enterPin
                firstPin == null -> S.newPin
                else -> S.confirmPin
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (hint != null && creating && firstPin == null) {
                Spacer(Modifier.height(6.dp))
                Text(hint, style = MaterialTheme.typography.bodySmall, color = Palette.textMuted, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .size(16.dp)
                            .background(if (i < entered.length) Palette.accent else Color.Transparent, CircleShape)
                            .border(2.dp, if (i < entered.length) Palette.accent else Palette.textMuted, CircleShape),
                    )
                }
            }
            Text(
                error ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.favorite,
                modifier = Modifier.padding(top = 10.dp).height(20.dp),
            )
            NumberPad(onDigit = ::type, onDelete = { entered = entered.dropLast(1) })
            if (!creating) {
                Spacer(Modifier.height(14.dp))
                ChipButton(S.forgotPin, selected = false, onClick = { forgot = true; error = null })
            }
        }
    }
}

@Composable
private fun NumberPad(onDigit: (Char) -> Unit, onDelete: () -> Unit) {
    val five = remember { FocusRequester() }
    LaunchedEffect(Unit) { five.requestSafely() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("123", "456", "789", "<0").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    when (c) {
                        '<' -> PadKey(onClick = onDelete) { Icon(Icons.AutoMirrored.Outlined.Backspace, null, Modifier.size(22.dp)) }
                        else -> PadKey(onClick = { onDigit(c) }, modifier = if (c == '5') Modifier.focusRequester(five) else Modifier) {
                            Text(c.toString(), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                if (row == "<0") Spacer(Modifier.size(76.dp, 52.dp))
            }
        }
    }
}

@Composable
private fun PadKey(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.size(76.dp, 52.dp).tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.surfaceFocused,
            contentColor = Palette.text,
            focusedContainerColor = Palette.text,
            focusedContentColor = Palette.background,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Palette.accent), shape = shape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun ForgotPin(password: String, onPassword: (String) -> Unit, error: String?, onNext: () -> Unit) {
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { field.requestSafely() }
    Text(S.forgotPin, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(6.dp))
    Text(S.forgotPinHint, style = MaterialTheme.typography.bodySmall, color = Palette.textMuted, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    TvTextField(
        value = password,
        onValueChange = onPassword,
        placeholder = S.password,
        password = true,
        imeAction = ImeAction.Done,
        onImeAction = onNext,
        modifier = Modifier.fillMaxWidth().focusRequester(field),
    )
    Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = Palette.favorite, modifier = Modifier.padding(top = 8.dp).height(20.dp))
    ActionButton(S.next, null, primary = true, onClick = onNext)
}

internal fun digitOf(key: Key): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

/** Settings tab while a PIN protects it. */
@Composable
fun SettingsLockedPane(vm: AppViewModel) {
    var asking by remember { mutableStateOf(false) }
    CenteredMessage(S.settingsLockedTitle, S.settingsLockedHint) {
        Spacer(Modifier.height(20.dp))
        ActionButton(S.unlock, Icons.Outlined.Lock, primary = true, onClick = { asking = true })
    }
    if (asking) PinDialog(vm, create = false, onSuccess = { asking = false; vm.settingsUnlocked = true }, onDismiss = { asking = false })
}

/** Section at the top of Settings: current profile, switching and managing, and the PIN. */
@Composable
fun ProfileSettings(vm: AppViewModel) {
    var pinDialog by remember { mutableStateOf(false) }
    Text(S.profilesSection, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
    Text(S.profilesHint, style = MaterialTheme.typography.bodyMedium, color = Palette.textMuted)
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ProfileAvatar(vm.activeProfile, vm.profileName(vm.activeProfile), 44.dp)
        Text(vm.profileName(vm.activeProfile), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        if (vm.profiles.size > 1) ActionButton(S.switchProfile, Icons.Outlined.People, onClick = { vm.profileScreen = ProfileMode.PICK })
        ActionButton(S.manageProfiles, Icons.Outlined.Edit, onClick = { vm.profileScreen = ProfileMode.MANAGE })
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ActionButton(if (vm.hasPin) S.changePin else S.setPin, Icons.Outlined.Lock, onClick = { pinDialog = true })
        if (vm.hasPin) ActionButton(S.removePin, null, onClick = { vm.setPin(null) })
    }
    if (pinDialog) PinDialog(vm, create = true, onSuccess = { pinDialog = false; vm.settingsUnlocked = true }, onDismiss = { pinDialog = false })
}
