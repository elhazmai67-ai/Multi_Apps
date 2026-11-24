package com.linkvault.app.ui.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.linkvault.app.tools.ImageEngine
import com.linkvault.app.tools.OfficeEngine
import com.linkvault.app.tools.OutFile
import com.linkvault.app.tools.PdfEngine
import com.linkvault.app.tools.ToolException
import com.linkvault.app.tools.ToolRunner
import com.linkvault.app.ui.components.*
import com.linkvault.app.ui.components.NeuButton as Button
import com.linkvault.app.ui.components.NeuTonalButton as FilledTonalButton
import com.linkvault.app.ui.components.NeuOutlinedButton as OutlinedButton

private val OFFICE_MIMES = arrayOf(
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/octet-stream"
)

@Composable
fun CompressScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val id = "compress"
    var mode by remember { mutableIntStateOf(0) } // 0 photos, 1 pdf, 2 documents
    val mimes = when (mode) { 0 -> arrayOf("image/*"); 1 -> arrayOf("application/pdf"); else -> OFFICE_MIMES }
    val pick = rememberPicker(mimes, true)
    LaunchedEffect(mode) { pick.files.clear() }
    var level by remember { mutableIntStateOf(1) }

    ToolPage("Compress", "Make photos, PDFs and documents smaller", onBack) {
        ChipRows(listOf("Photos", "PDF", "Documents").mapIndexed { i, l -> l to (i == mode) }, 3) { mode = it }
        FilePickerCard(when (mode) { 0 -> "Choose photos"; 1 -> "Choose PDFs"; else -> "Choose Word, Excel or PowerPoint files" }, pick)
        OptionLabel("How much?")
        ChipRows(listOf("Smallest file", "Balanced", "Best quality").mapIndexed { i, l -> l to (i == level) }, 3) { level = it }
        Text(
            when (mode) {
                0 -> "Photos are resized and saved as JPG. Transparent PNGs become WebP."
                1 -> "Every page is redrawn as a compressed picture, so the text can no longer be selected. A PDF that is mostly text may not get smaller."
                else -> "The pictures inside the file are shrunk. Text and layout stay the same."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RunButton("Compress", pick.files.isNotEmpty(), id) {
            val files = pick.files.toList()
            val m = mode
            val lv = level
            val total = files.sumOf { it.size.coerceAtLeast(0) }
            ToolRunner.start(id, context, total) { tc ->
                files.mapIndexed { i, f ->
                    tc.checkCancelled()
                    tc.progress(i / files.size.toFloat(), "File ${i + 1} of ${files.size}")
                    when (m) {
                        0 -> ImageEngine.compress(tc, f, lv)
                        1 -> PdfEngine.compressPdf(tc, tc.copyIn(f), f.baseName, lv)
                        else -> {
                            val src = tc.copyIn(f)
                            val ext = f.ext.ifBlank { "docx" }
                            val out = tc.tempFile("${f.baseName.take(80)}_compressed.$ext")
                            OfficeEngine.compressOffice(tc, src, out, lv)
                            if (out.length() >= src.length()) throw ToolException("${f.name} is already small. Nothing to save.")
                            OutFile(out)
                        }
                    }
                }
            }
        }
        ToolResultPanel(id)
    }
}

@Composable
fun UnlockScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val id = "unlock"
    val pick = rememberPicker(arrayOf("application/pdf", "application/zip", "application/x-zip-compressed"), false)
    var password by remember { mutableStateOf("") }

    ToolPage("Unlock", "Remove the password from a bank statement or other PDF", onBack) {
        FilePickerCard("Choose the locked file", pick)
        OptionLabel("Password of the file")
        NeuTextField(
            password, { password = it }, "Enter the password",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation()
        )
        Text(
            "Use the password your bank gave you for this statement. You get an unlocked copy; the original stays as it is. The password is only used on this phone and isn't saved.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RunButton("Unlock", pick.files.isNotEmpty(), id) {
            val f = pick.files.first()
            val pw = password
            ToolRunner.start(id, context, f.size) { tc ->
                tc.progress(-1f, "Unlocking")
                val src = tc.copyIn(f)
                if (f.ext == "zip") PdfEngine.unlockZip(tc, src, pw) else listOf(PdfEngine.unlockPdf(tc, src, pw, f.baseName))
            }
        }
        ToolResultPanel(id)
    }
}

@Composable
fun ProtectScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val id = "protect"
    val pick = rememberPicker(arrayOf("*/*"), true)
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val mismatch = again.isNotEmpty() && again != password
    val onlyPdf = pick.files.size == 1 && pick.files[0].ext == "pdf"

    ToolPage("Protect", "Lock a file with a password", onBack) {
        FilePickerCard("Choose files to lock", pick)
        OptionLabel("Choose a password")
        NeuTextField(
            password, { password = it }, "Password",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation()
        )
        NeuTextField(
            again, { again = it }, "Type it again",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation()
        )
        if (mismatch) Text("The two passwords don't match.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Text(
            if (onlyPdf) "A PDF gets a real PDF password. Any PDF app will ask for it."
            else "These files are put into one locked ZIP (AES-256). Open it in LinkVault with Tools → Unlock, or in any app that supports ZIP passwords.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("If you forget the password, the file can't be opened again. Nobody can recover it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RunButton("Protect", pick.files.isNotEmpty() && password.length >= 4 && password == again, id) {
            val files = pick.files.toList()
            val pw = password
            ToolRunner.start(id, context) { tc ->
                if (files.size == 1 && files[0].ext == "pdf") {
                    listOf(PdfEngine.protectPdf(tc, tc.copyIn(files[0]), pw, files[0].baseName))
                } else {
                    val prepared = files.map { tc.copyIn(it) to it.name }
                    listOf(PdfEngine.protectAsZip(tc, prepared, pw, if (files.size == 1) files[0].baseName else "locked_files"))
                }
            }
        }
        ToolResultPanel(id)
    }
}
