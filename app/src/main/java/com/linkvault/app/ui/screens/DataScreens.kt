package com.linkvault.app.ui.screens

import com.linkvault.app.ui.components.NeuButton as Button
import com.linkvault.app.ui.components.NeuTonalButton as FilledTonalButton
import com.linkvault.app.ui.components.NeuOutlinedButton as OutlinedButton
import com.linkvault.app.ui.components.NeuIconButton as IconButton
import com.linkvault.app.ui.components.NeuSwitch as Switch
import com.linkvault.app.ui.components.NeuCheckbox as Checkbox
import com.linkvault.app.ui.components.neuRaised
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.linkvault.app.MainViewModel
import com.linkvault.app.data.Backup
import com.linkvault.app.data.BackupFormatException
import com.linkvault.app.data.ImportMode
import com.linkvault.app.ui.components.ConfirmDialog
import com.linkvault.app.ui.components.SectionHeader
import com.linkvault.app.ui.theme.Radius
import com.linkvault.app.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_IMPORT_BYTES = 25L * 1024 * 1024

private suspend fun writeText(context: Context, uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
    } catch (e: Exception) { false }
}

private suspend fun readText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            // Manual bounded read (InputStream.readNBytes needs API 33).
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_IMPORT_BYTES) return@use null
                out.write(buf, 0, n)
            }
            String(out.toByteArray(), Charsets.UTF_8)
        }
    } catch (e: Exception) { null }
}

/** "Backup & Restore" (kind = backup) and "Export & Import" (kind = transfer). */
@Composable
fun DataScreen(vm: MainViewModel, backupMode: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val stats by vm.stats.collectAsState()

    // --- Export / backup writers ---
    val jsonSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = writeText(context, uri, vm.repo.exportJson())
            if (ok) { vm.markBackup(); vm.toast(if (backupMode) "Backup created" else "Exported as JSON") }
            else vm.toast("Couldn't save the file. Please try another location.")
        }
    }
    val csvSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val ok = writeText(context, uri, vm.repo.exportCsv())
            vm.toast(if (ok) "Exported as CSV" else "Couldn't save the file. Please try another location.")
        }
    }

    // --- Import / restore ---
    var pendingText by remember { mutableStateOf<String?>(null) }
    var pendingCount by remember { mutableIntStateOf(0) }
    var confirmReplace by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = readText(context, uri)
            if (text == null) { vm.toast("Couldn't read that file. It may be too large or unavailable."); return@launch }
            try {
                val parsed = withContext(Dispatchers.Default) { Backup.parse(text, System.currentTimeMillis()) }
                pendingCount = parsed.links.size
                pendingText = text
            } catch (e: BackupFormatException) {
                vm.toast(e.message ?: "This file isn't a valid LinkVault backup.")
            } catch (e: Exception) {
                vm.toast("This file isn't a valid LinkVault backup.")
            }
        }
    }

    fun runImport(mode: ImportMode) {
        val text = pendingText ?: return
        pendingText = null; busy = true
        scope.launch {
            try {
                val r = vm.repo.importData(text, mode)
                resultMessage = buildString {
                    append("${r.added} added")
                    if (r.updated > 0) append(", ${r.updated} updated")
                    if (r.skipped > 0) append(", ${r.skipped} duplicates skipped")
                    if (r.invalid > 0) append(", ${r.invalid} invalid entries ignored")
                    append(".")
                }
            } catch (e: BackupFormatException) {
                vm.toast(e.message ?: "This file isn't a valid LinkVault backup.")
            } catch (e: Exception) {
                vm.toast("Something went wrong while importing. Your existing links were not changed.")
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text(if (backupMode) "Backup & Restore" else "Export & Import", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            if (backupMode) {
                SectionHeader("Backup")
                Text("Last backup", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    settings?.lastBackupAt?.takeIf { it > 0 }?.let { Format.full(it) } ?: "Never",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "A backup is a single file containing all ${stats.total} links and your categories. It is saved wherever you choose. No account needed.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = { jsonSaver.launch("linkvault-backup-${Format.fileStamp()}.json") }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = Radius.pill) { Text("Create Backup") }

                SectionHeader("Restore")
                Text("Choose a LinkVault backup file. You'll pick how it is combined with your current links before anything changes.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = Radius.pill) { Text("Restore from file") }
            } else {
                SectionHeader("Export")
                Text("Export all ${stats.total} links to a file you can keep, open in a spreadsheet, or import later.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { jsonSaver.launch("linkvault-export-${Format.fileStamp()}.json") }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = Radius.pill) { Text("Export as JSON") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { csvSaver.launch("linkvault-export-${Format.fileStamp()}.csv") }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = Radius.pill) { Text("Export as CSV") }

                SectionHeader("Import")
                Text("Import a LinkVault JSON file. Existing links are never deleted unless you choose Replace.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = Radius.pill) { Text("Import from file") }
            }
            if (busy) { Spacer(Modifier.height(16.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
    }

    if (pendingText != null) {
        AlertDialog(
            onDismissRequest = { pendingText = null }, shape = Radius.card,
            title = { Text("Import $pendingCount link${if (pendingCount == 1) "" else "s"}") },
            text = { Text("How should these be combined with the ${stats.total} links you already have?") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { runImport(ImportMode.MERGE_SKIP_DUPLICATES) }) { Text("Merge, skip duplicates") }
                    TextButton(onClick = { runImport(ImportMode.MERGE_UPDATE_DUPLICATES) }) { Text("Merge, update duplicates") }
                    TextButton(onClick = { confirmReplace = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Replace existing data") }
                }
            },
            dismissButton = { TextButton(onClick = { pendingText = null }) { Text("Cancel") } }
        )
    }
    if (confirmReplace) ConfirmDialog(
        title = "Replace all data?",
        text = "Your ${stats.total} current links and categories will be removed and replaced by the file's contents. Create a backup first if you're unsure.",
        confirmLabel = "Replace", destructive = true,
        onConfirm = { confirmReplace = false; runImport(ImportMode.REPLACE) },
        onDismiss = { confirmReplace = false }
    )
    resultMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { resultMessage = null }, shape = Radius.card,
            title = { Text("Import complete") }, text = { Text(msg) },
            confirmButton = { TextButton(onClick = { resultMessage = null }) { Text("OK") } }
        )
    }
}
