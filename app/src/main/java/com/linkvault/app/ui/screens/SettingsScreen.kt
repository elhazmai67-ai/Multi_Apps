package com.linkvault.app.ui.screens

import com.linkvault.app.ui.components.NeuButton as Button
import com.linkvault.app.ui.components.NeuTonalButton as FilledTonalButton
import com.linkvault.app.ui.components.NeuOutlinedButton as OutlinedButton
import com.linkvault.app.ui.components.NeuIconButton as IconButton
import com.linkvault.app.ui.components.NeuSwitch as Switch
import com.linkvault.app.ui.components.NeuCheckbox as Checkbox
import com.linkvault.app.ui.components.neuRaised
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkvault.app.MainViewModel
import com.linkvault.app.data.ThemeMode
import com.linkvault.app.download.CookieStore
import com.linkvault.app.ui.components.*
import com.linkvault.app.ui.theme.Radius
import com.linkvault.app.util.Biometrics
import com.linkvault.app.util.Format
import com.linkvault.app.util.OpenMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, navigate: (String) -> Unit) {
    val context = LocalContext.current
    var hasCookies by remember { mutableStateOf(CookieStore.has(context)) }
    val cookiePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = CookieStore.import(context, uri)
            hasCookies = CookieStore.has(context)
            vm.toast(if (ok) "Login cookies imported" else "That doesn't look like a cookies.txt file")
        }
    }
    val settings by vm.settings.collectAsState()
    val s = settings ?: return
    var pinSetup by remember { mutableStateOf(false) }
    var pinChange by remember { mutableStateOf(false) }
    var pinDisable by remember { mutableStateOf(false) }
    var qualitySheet by remember { mutableStateOf(false) }
    var overlayDialog by remember { mutableStateOf(false) }
    var waitingForOverlay by remember { mutableStateOf(false) }
    val overlayAllowed = android.provider.Settings.canDrawOverlays(context)

    // Back from Android's "display over other apps" screen: switch the button on if it was allowed.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, waitingForOverlay) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && waitingForOverlay) {
                waitingForOverlay = false
                if (android.provider.Settings.canDrawOverlays(context)) vm.setFloatingButton(true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val bioAvailable = remember { Biometrics.isAvailable(context) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        ScreenTitle("Settings", subtitle = "Make LinkVault yours.")

        Group("Appearance") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                val modes = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                modes.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = s.theme == mode, onClick = { vm.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size), label = { Text(label) }
                    )
                }
            }
        }

        Group("Link behavior") {
            OpenMode.entries.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { vm.setOpenMode(m) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = s.openMode == m, onClick = null)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(m.label, style = MaterialTheme.typography.bodyLarge)
                        Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Group("Home") {
            ToggleRow("Suggest copied links", "Offer to save a link you've just copied", s.clipboardSuggestions) { vm.setClipboardSuggestions(it) }
            ToggleRow("Show most used", "A short list of the links you open most", s.showMostUsed) { vm.setShowMostUsed(it) }
            ToggleRow("Fetch titles & thumbnails", "When adding a link, look up its real title and, for videos, its cover image from the site itself.", s.fetchTitles) { vm.setFetchTitles(it) }
        }

        Group("Floating button") {
            ToggleRow(
                "Show floating button",
                "After you minimize LinkVault, a small button stays over other apps. Tap it to paste a link and save or download it. Tap the small × on it to hide it.",
                s.floatingButton && overlayAllowed
            ) { on ->
                if (!on) vm.setFloatingButton(false)
                else if (overlayAllowed) vm.setFloatingButton(true)
                else overlayDialog = true
            }
        }

        Group("Downloads") {
            NavRow("Download queue", "Videos, audio, images, PDFs and files") { navigate("downloads") }
            NavRow("Default quality", s.downloadQuality.label) { qualitySheet = true }
            NavRow("Update yt-dlp", "Fixes downloads that stopped working for a site") { vm.updateDownloader() }
            NavRow(
                "Login cookies",
                if (hasCookies) "Imported. Used for Instagram stories and private posts" else "For Instagram stories and private posts. Pick a cookies.txt file"
            ) { cookiePicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) }
            if (hasCookies) NavRow("Remove login cookies", "Delete the imported cookies from this phone") {
                CookieStore.clear(context); hasCookies = false; vm.toast("Login cookies removed")
            }
        }

        Group("Data") {
            NavRow("Backup & Restore", s.lastBackupAt.takeIf { it > 0 }?.let { "Last backup: ${Format.full(it)}" } ?: "No backup yet") { navigate("backup") }
            NavRow("Export & Import", "JSON or CSV") { navigate("transfer") }
            NavRow("Clean up links", "Find duplicate and dead links, delete them in one tap") { navigate("cleanup") }
        }

        Group("Privacy") {
            ToggleRow("App lock", "Require a PIN when opening LinkVault", s.appLockEnabled) { on ->
                if (on) pinSetup = true else pinDisable = true
            }
            if (s.appLockEnabled) {
                if (bioAvailable) ToggleRow("Biometric unlock", "Use fingerprint or face unlock instead of the PIN", s.biometricEnabled) { vm.setBiometric(it) }
                NavRow("Change PIN", null) { pinChange = true }
            }
        }

        Group("Organization") {
            NavRow("Manage categories", null) { navigate("manage_categories") }
            NavRow("Manage tags", null) { navigate("manage_tags") }
        }

        Group("About") {
            NavRow("About LinkVault", appVersion(context)) { navigate("about") }
            NavRow("Privacy information", "Where your data lives") { navigate("privacy_info") }
        }
    }

    if (overlayDialog) AlertDialog(
        onDismissRequest = { overlayDialog = false }, shape = Radius.card,
        title = { Text("Allow display over other apps") },
        text = { Text("To show the floating button, Android needs your permission to display Multi Apps over other apps. On the next screen turn on \"Allow display over other apps\", then come back.") },
        confirmButton = {
            TextButton(onClick = {
                overlayDialog = false
                waitingForOverlay = true
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + context.packageName))
                )
            }) { Text("Open settings") }
        },
        dismissButton = { TextButton(onClick = { overlayDialog = false }) { Text("Not now") } }
    )
    if (qualitySheet) DownloadOptionsSheet(
        title = "Default download quality", subtitle = null, initial = s.downloadQuality, confirmLabel = "Save", showNotice = false,
        onConfirm = { q -> vm.setDownloadQuality(q); qualitySheet = false }, onDismiss = { qualitySheet = false }
    )
    if (pinSetup) PinSetupDialog(
        title = "Set a PIN", onDismiss = { pinSetup = false },
        onConfirm = { pin -> vm.enableLock(pin); pinSetup = false; vm.toast("App lock is on") }
    )
    if (pinChange) PinSetupDialog(
        title = "New PIN", onDismiss = { pinChange = false },
        onConfirm = { pin -> vm.enableLock(pin); pinChange = false; vm.toast("PIN changed") }
    )
    if (pinDisable) PinVerifyDialog(
        vm = vm, title = "Turn off app lock", message = "Enter your PIN to turn off app lock.",
        onDismiss = { pinDisable = false },
        onVerified = { vm.disableLock(); pinDisable = false; vm.toast("App lock is off") }
    )
}

fun appVersion(context: android.content.Context): String = try {
    "Version " + context.packageManager.getPackageInfo(context.packageName, 0).versionName
} catch (e: Exception) { "Version 1.0.0" }

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp, top = 22.dp, bottom = 8.dp)
    )
    Surface(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().neuRaised(Radius.card, 5.dp), shape = Radius.card, color = MaterialTheme.colorScheme.surface
    ) { Column(Modifier.padding(vertical = 4.dp), content = content) }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onChange(!checked) }.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun NavRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun PinSetupDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, shape = Radius.card, title = { Text(title) },
        text = {
            Column {
                Text("Use 4 to 8 digits. If you forget it, the only way back in is to clear the app's data, which erases your links, so keep a backup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                PinField(pin, "PIN") { pin = it.filter(Char::isDigit).take(8); error = null }
                Spacer(Modifier.height(8.dp))
                PinField(again, "Confirm PIN", error) { again = it.filter(Char::isDigit).take(8); error = null }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin.length < 4 -> error = "PIN must be at least 4 digits."
                    pin != again -> error = "The two PINs don't match."
                    else -> onConfirm(pin)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PinVerifyDialog(vm: MainViewModel, title: String, message: String, onDismiss: () -> Unit, onVerified: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss, shape = Radius.card, title = { Text(title) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                PinField(pin, "PIN", error) { pin = it.filter(Char::isDigit).take(8); error = null }
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { if (vm.verifyPin(pin)) onVerified() else error = "Incorrect PIN." } }) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun PinField(value: String, label: String, error: String? = null, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        isError = error != null, supportingText = { error?.let { Text(it) } }, shape = Radius.small,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier.fillMaxWidth()
    )
}

// ---------------------------------------------------------------- Tags

@Composable
fun ManageTagsScreen(vm: MainViewModel, onBack: () -> Unit, onShowTag: (String) -> Unit) {
    val tags by vm.tagCounts.collectAsState()
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Manage tags", style = MaterialTheme.typography.titleLarge)
        }
        if (tags.isEmpty()) {
            EmptyState("No tags yet", "Add tags when saving a link, like \"revision\" or \"recipes\", and they'll show up here.")
        } else {
            androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
                items(tags.size, key = { tags[it].name.lowercase() }) { i ->
                    val t = tags[i]
                    Surface(
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().neuRaised(Radius.card, 5.dp), shape = Radius.card,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(Modifier.heightIn(min = 56.dp).clickable { onShowTag(t.name) }.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("#${t.name}", style = MaterialTheme.typography.titleSmall)
                                Text("${t.count} link${if (t.count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { renaming = t.name }) { Text("Rename") }
                            TextButton(onClick = { deleting = t.name }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    renaming?.let { old ->
        TextInputDialog(
            title = "Rename tag", label = "Tag name", confirmLabel = "Rename", initial = old,
            validate = { if (it.isBlank()) "Enter a tag name" else null },
            onConfirm = { new -> scope.launch { vm.repo.renameTag(old, new) }; renaming = null; vm.toast("Tag renamed") },
            onDismiss = { renaming = null }
        )
    }
    deleting?.let { name ->
        val n = tags.firstOrNull { it.name == name }?.count ?: 0
        ConfirmDialog(
            title = "Delete tag \"#$name\"?", text = "It will be removed from $n link${if (n == 1) "" else "s"}. The links themselves are kept.",
            confirmLabel = "Delete", destructive = true,
            onConfirm = { scope.launch { vm.repo.deleteTag(name) }; deleting = null; vm.toast("Tag deleted") },
            onDismiss = { deleting = null }
        )
    }
}

// ---------------------------------------------------------------- About / privacy

@Composable
fun InfoScreen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), content = content)
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    InfoScreen("About LinkVault", onBack) {
        Text("Multi Apps", style = MaterialTheme.typography.headlineMedium)
        Text(appVersion(context), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(
            "A private place for the links that matter: videos, notes, shopping, study material and more. Save from any app with Share, find anything instantly, and open it in the right app with one tap.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))
        Text("No account. No ads. No analytics. Works fully offline.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PrivacyInfoScreen(onBack: () -> Unit) {
    InfoScreen("Privacy information", onBack) {
        PrivacyBlock("Stored on this device only", "Your links, categories, tags, notes and usage counts live in a private database on this phone. LinkVault has no server and no account, and it never uploads your data.")
        PrivacyBlock("No tracking", "There are no analytics, advertising or crash-reporting libraries in the app. Open counts and last-opened dates are kept locally so you can sort by Most used.")
        PrivacyBlock("Websites you open", "Tapping a link hands it to another app or your browser. That app or website then follows its own privacy rules, which are separate from LinkVault.")
        PrivacyBlock("Optional titles and thumbnails", "If \"Fetch titles & thumbnails\" is on, adding a link makes a small request to that same website to read its title and icon. For YouTube links it asks YouTube's own title and image servers, and for other video sites the cover image named on the page. Covers are stored on this device. Nothing goes to any other party, and you can turn this off in Settings.")
        PrivacyBlock("Downloads", "Only when you tap Download, LinkVault connects to the site that hosts the link and saves the file into the LinkVault folder in Movies, Music, Pictures or Download on this phone. If you import a cookies.txt, it stays in the app's private storage and is only used for your own downloads. The downloader (yt-dlp) runs on your device, no link or file goes through a LinkVault server, and \"Update yt-dlp\" in Settings fetches the downloader itself from its public release page. Only download content you have the right to save.")
        PrivacyBlock("Floating button", "Optional and off by default. It needs Android's \"display over other apps\" permission. The button only shows a shortcut; your clipboard is read only when you open the panel from it, and nothing is sent anywhere until you tap Save or Download.")
        PrivacyBlock("Clipboard", "When \"Suggest copied links\" is on, LinkVault looks at your clipboard when Home opens, only to offer saving a link you copied. It's never stored unless you save it.")
        PrivacyBlock("Backups", "Backup and export files are written only where you choose. Anyone with the file can read your links, so store it somewhere you trust.")
        PrivacyBlock("App lock", "Your PIN is stored as a salted hash, never in plain text. Biometric unlock uses Android's own system and LinkVault never sees your fingerprint or face data.")
    }
}

@Composable
private fun PrivacyBlock(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}
