package com.linkvault.app.ui.tools

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

@Composable
fun ImageConvertScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val id = "image_convert"
    val pick = rememberPicker(arrayOf("image/*"), true)
    var format by remember { mutableIntStateOf(0) }
    var quality by remember { mutableFloatStateOf(90f) }
    val ext = ImageEngine.outputFormats[format]

    ToolPage("Image converter", "Change the format of your pictures", onBack) {
        FilePickerCard("Choose pictures", pick)
        OptionLabel("Convert to")
        ChipRows(ImageEngine.outputFormats.mapIndexed { i, f -> f to (i == format) }, 4) { format = it }
        if (ext == "jpg" || ext == "webp") {
            OptionLabel("Quality ${quality.toInt()}%")
            Slider(value = quality, onValueChange = { quality = it }, valueRange = 30f..100f)
        }
        Text(
            "Works with jpg, png, webp, gif, bmp and heic pictures. All the pictures you choose are converted to the same format.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RunButton("Convert", pick.files.isNotEmpty(), id) {
            val files = pick.files.toList()
            val e = ext
            val q = quality.toInt()
            ToolRunner.start(id, context) { tc ->
                files.mapIndexed { i, f ->
                    tc.checkCancelled()
                    tc.progress(i / files.size.toFloat(), "Converting ${i + 1} of ${files.size}")
                    ImageEngine.convert(tc, f, e, q)
                }
            }
        }
        ToolResultPanel(id)
    }
}

private val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

private enum class Conv(val label: String, val mimes: Array<String>, val multiple: Boolean, val chooseLabel: String) {
    PDF_PNG("PDF → PNG", arrayOf("application/pdf"), false, "Choose a PDF"),
    PDF_JPG("PDF → JPG", arrayOf("application/pdf"), false, "Choose a PDF"),
    IMG_PDF("JPG / PNG → PDF", arrayOf("image/jpeg", "image/png"), true, "Choose pictures"),
    PDF_DOCX("PDF → Word", arrayOf("application/pdf"), false, "Choose a PDF"),
    DOCX_PDF("Word → PDF", arrayOf(DOCX, "application/octet-stream"), false, "Choose a .docx file"),
    PDF_XLSX("PDF → Excel", arrayOf("application/pdf"), false, "Choose a PDF"),
    XLSX_PDF("Excel → PDF", arrayOf(XLSX, "application/octet-stream"), false, "Choose an .xlsx file"),
    PDF_HTML("PDF → HTML", arrayOf("application/pdf"), false, "Choose a PDF"),
    HTML_PDF("HTML → PDF", arrayOf("text/html", "application/xhtml+xml", "application/octet-stream"), false, "Choose an .html file")
}

@Composable
fun DocConvertScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val id = "doc_convert"
    var sel by remember { mutableIntStateOf(0) }
    val conv = Conv.entries[sel]
    val pick = rememberPicker(conv.mimes, conv.multiple)
    LaunchedEffect(sel) { pick.files.clear() }

    ToolPage("Document converter", "PDF, Word, Excel, HTML and pictures", onBack) {
        OptionLabel("What do you want to convert?")
        ChipRows(Conv.entries.mapIndexed { i, c -> c.label to (i == sel) }, 2) { sel = it }
        FilePickerCard(conv.chooseLabel, pick)
        val textBased = conv in setOf(Conv.PDF_DOCX, Conv.DOCX_PDF, Conv.PDF_XLSX, Conv.XLSX_PDF, Conv.PDF_HTML, Conv.HTML_PDF)
        if (textBased) Text(
            "Converts text and simple tables. Pictures, fonts and exact layout aren't kept. A scanned PDF (only pictures) has no text to convert.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RunButton("Convert", pick.files.isNotEmpty(), id) {
            val files = pick.files.toList()
            val c = conv
            ToolRunner.start(id, context) { tc ->
                if (c == Conv.IMG_PDF) {
                    listOf(PdfEngine.imagesToPdf(tc, files, if (files.size == 1) files[0].baseName else "images"))
                } else {
                    val f = files.first()
                    val src = tc.copyIn(f)
                    val base = f.baseName.take(80)
                    when (c) {
                        Conv.PDF_PNG -> PdfEngine.pdfToImages(tc, src, base, true)
                        Conv.PDF_JPG -> PdfEngine.pdfToImages(tc, src, base, false)
                        Conv.PDF_DOCX -> {
                            val pages = PdfEngine.extractPages(tc, src)
                            val out = tc.tempFile("$base.docx")
                            OfficeEngine.writeDocx(pages, out)
                            tc.note = "Text only. Open it in Word and check the layout."
                            listOf(OutFile(out))
                        }
                        Conv.PDF_XLSX -> {
                            val pages = PdfEngine.extractPages(tc, src)
                            val out = tc.tempFile("$base.xlsx")
                            OfficeEngine.writeXlsx(OfficeEngine.textToRows(pages), out)
                            tc.note = "Each line became a row; columns were split where there were wide gaps."
                            listOf(OutFile(out))
                        }
                        Conv.PDF_HTML -> {
                            val pages = PdfEngine.extractPages(tc, src)
                            val out = tc.tempFile("$base.html")
                            out.writeText(OfficeEngine.textToHtml(pages, base))
                            listOf(OutFile(out))
                        }
                        Conv.DOCX_PDF -> {
                            tc.progress(-1f, "Reading the document")
                            val text = OfficeEngine.readDocx(src)
                            val out = tc.tempFile("$base.pdf")
                            tc.progress(-1f, "Making the PDF")
                            OfficeEngine.spannedToPdf(text, out)
                            listOf(OutFile(out))
                        }
                        Conv.XLSX_PDF -> {
                            tc.progress(-1f, "Reading the spreadsheet")
                            val sheets = OfficeEngine.readXlsx(src)
                            val out = tc.tempFile("$base.pdf")
                            tc.progress(-1f, "Making the PDF")
                            OfficeEngine.sheetsToPdf(sheets, out)
                            tc.note = "Dates may show as numbers. Spreadsheet formatting isn't kept."
                            listOf(OutFile(out))
                        }
                        Conv.HTML_PDF -> {
                            val html = src.readText()
                            if (html.isBlank()) throw ToolException("This HTML file is empty.")
                            val out = tc.tempFile("$base.pdf")
                            OfficeEngine.spannedToPdf(OfficeEngine.htmlToSpanned(html), out)
                            tc.note = "Text and basic styles only. Pictures aren't included."
                            listOf(OutFile(out))
                        }
                        Conv.IMG_PDF -> emptyList()
                    }
                }
            }
        }
        ToolResultPanel(id)
    }
}
