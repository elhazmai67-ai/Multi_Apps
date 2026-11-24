package com.linkvault.app.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Html
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Word / Excel / HTML reading and writing done on the phone, with plain text and tables (no fancy layout). */
object OfficeEngine {
    private val INVALID_XML = Regex("[^\\u0009\\u000A\\u000D\\u0020-\\uD7FF\\uE000-\\uFFFD]")

    private fun esc(s: String): String =
        INVALID_XML.replace(s, "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun zip(out: File, parts: List<Pair<String, String>>) {
        ZipOutputStream(FileOutputStream(out)).use { z ->
            parts.forEach { (name, text) ->
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
    }

    // ---------------------------------------------------------------- writers

    /** Text pages to a .docx file (one paragraph per line, a page break between pages). */
    fun writeDocx(pages: List<String>, out: File) {
        val body = StringBuilder()
        pages.forEachIndexed { i, text ->
            text.lines().forEach { line ->
                body.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(esc(line)).append("</w:t></w:r></w:p>")
            }
            if (i < pages.lastIndex) body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
        }
        zip(out, listOf(
            "[Content_Types].xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>",
            "_rels/.rels" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>",
            "word/document.xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>$body<w:sectPr/></w:body></w:document>"
        ))
    }

    private val NUMBER = Regex("^-?\\d{1,3}(,\\d{3})*(\\.\\d+)?$|^-?\\d+(\\.\\d+)?$")

    private fun colName(i: Int): String {
        var n = i
        val sb = StringBuilder()
        do { sb.insert(0, ('A' + n % 26)); n = n / 26 - 1 } while (n >= 0)
        return sb.toString()
    }

    /** Rows of cells to a .xlsx file. Plain numbers become numbers; everything else stays text. */
    fun writeXlsx(rows: List<List<String>>, out: File) {
        val sheet = StringBuilder()
        rows.forEachIndexed { r, cells ->
            sheet.append("<row r=\"${r + 1}\">")
            cells.forEachIndexed { c, raw ->
                val v = raw.trim()
                if (v.isEmpty()) return@forEachIndexed
                val ref = colName(c) + (r + 1)
                val plain = v.replace(",", "")
                val isNum = NUMBER.matches(v) && plain.length <= 15 && !(plain.length > 1 && plain.startsWith("0") && !plain.startsWith("0."))
                if (isNum) sheet.append("<c r=\"$ref\"><v>$plain</v></c>")
                else sheet.append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(esc(v)).append("</t></is></c>")
            }
            sheet.append("</row>")
        }
        zip(out, listOf(
            "[Content_Types].xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>",
            "_rels/.rels" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>",
            "xl/workbook.xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>",
            "xl/_rels/workbook.xml.rels" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>",
            "xl/worksheets/sheet1.xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>$sheet</sheetData></worksheet>"
        ))
    }

    /** PDF text lines to table rows: cells are split where there are two or more spaces in a row. */
    fun textToRows(pages: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val split = Regex("\\s{2,}|\\t")
        pages.forEach { page ->
            page.lines().forEach { line ->
                if (line.isNotBlank()) rows.add(line.trim().split(split))
            }
        }
        return rows
    }

    fun textToHtml(pages: List<String>, title: String): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>")
            .append(esc(title)).append("</title><style>body{font-family:sans-serif;max-width:800px;margin:24px auto;padding:0 16px;line-height:1.5}section{margin-bottom:32px;border-bottom:1px solid #ddd}</style></head><body>")
        pages.forEach { page ->
            sb.append("<section>")
            var open = false
            page.lines().forEach { line ->
                if (line.isBlank()) { if (open) { sb.append("</p>"); open = false } }
                else { if (!open) { sb.append("<p>"); open = true } else sb.append("<br>"); sb.append(esc(line.trim())) }
            }
            if (open) sb.append("</p>")
            sb.append("</section>")
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    // ---------------------------------------------------------------- readers

    private fun parser(zip: ZipFile, name: String): XmlPullParser? {
        val entry = zip.getEntry(name) ?: return null
        val p = Xml.newPullParser()
        p.setInput(zip.getInputStream(entry), "UTF-8")
        return p
    }

    /** Text of a .docx as styled text (bold, italic, bigger headings). */
    fun readDocx(file: File): Spanned {
        val zip = try { ZipFile(file) } catch (e: Exception) { throw ToolException("This isn't a .docx file. Old .doc files aren't supported; save it as .docx first.") }
        zip.use { z ->
            val p = parser(z, "word/document.xml") ?: throw ToolException("This isn't a .docx file. Old .doc files aren't supported; save it as .docx first.")
            val sb = SpannableStringBuilder()
            var paraStart = 0
            var heading = 0
            var bold = false
            var italic = false
            var inText = false
            var tableDepth = 0
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "w:p" -> { paraStart = sb.length; heading = 0 }
                        "w:tbl" -> tableDepth++
                        "w:pStyle" -> {
                            val v = p.getAttributeValue(null, "w:val").orEmpty().lowercase()
                            heading = when {
                                v == "title" -> 1
                                v.startsWith("heading1") -> 1
                                v.startsWith("heading2") -> 2
                                v.startsWith("heading") -> 3
                                else -> 0
                            }
                        }
                        "w:r" -> { bold = false; italic = false }
                        "w:b" -> bold = p.getAttributeValue(null, "w:val").let { it != "0" && it != "false" }
                        "w:i" -> italic = p.getAttributeValue(null, "w:val").let { it != "0" && it != "false" }
                        "w:t" -> inText = true
                        "w:tab" -> sb.append('\t')
                        "w:br" -> sb.append('\n')
                    }
                    XmlPullParser.TEXT -> if (inText) {
                        val start = sb.length
                        sb.append(p.text)
                        if (bold) sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        if (italic) sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    XmlPullParser.END_TAG -> when (p.name) {
                        "w:t" -> inText = false
                        "w:p" -> {
                            if (heading > 0 && sb.length > paraStart) {
                                sb.setSpan(RelativeSizeSpan(listOf(1.6f, 1.35f, 1.15f)[heading - 1]), paraStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                                sb.setSpan(StyleSpan(Typeface.BOLD), paraStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                            if (tableDepth == 0) sb.append('\n')
                        }
                        "w:tc" -> sb.append("   |   ")
                        "w:tr" -> sb.append('\n')
                        "w:tbl" -> { tableDepth--; sb.append('\n') }
                    }
                }
                ev = p.next()
            }
            if (sb.isBlank()) throw ToolException("No text found in this document.")
            return sb
        }
    }

    /** Every sheet of an .xlsx as rows of text cells. */
    fun readXlsx(file: File): List<List<List<String>>> {
        val zip = try { ZipFile(file) } catch (e: Exception) { throw ToolException("This isn't an .xlsx file. Old .xls files aren't supported; save it as .xlsx first.") }
        zip.use { z ->
            if (z.getEntry("xl/workbook.xml") == null) throw ToolException("This isn't an .xlsx file. Old .xls files aren't supported; save it as .xlsx first.")
            val shared = ArrayList<String>()
            parser(z, "xl/sharedStrings.xml")?.let { p ->
                var sb: StringBuilder? = null
                var inT = false
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    when (ev) {
                        XmlPullParser.START_TAG -> when (p.name) { "si" -> sb = StringBuilder(); "t" -> inT = true }
                        XmlPullParser.TEXT -> if (inT) sb?.append(p.text)
                        XmlPullParser.END_TAG -> when (p.name) { "t" -> inT = false; "si" -> { shared.add(sb.toString()); sb = null } }
                    }
                    ev = p.next()
                }
            }
            val sheetNames = z.entries().asSequence().map { it.name }
                .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                .sortedBy { it.removePrefix("xl/worksheets/sheet").removeSuffix(".xml").toIntOrNull() ?: 0 }.toList()
            val sheets = ArrayList<List<List<String>>>()
            for (name in sheetNames) {
                val p = parser(z, name) ?: continue
                val rows = ArrayList<List<String>>()
                var row: ArrayList<String>? = null
                var type = ""
                var col = 0
                var value: StringBuilder? = null
                var inV = false
                var inIs = false
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    when (ev) {
                        XmlPullParser.START_TAG -> when (p.name) {
                            "row" -> row = ArrayList()
                            "c" -> {
                                type = p.getAttributeValue(null, "t").orEmpty()
                                val ref = p.getAttributeValue(null, "r").orEmpty().takeWhile { it.isLetter() }
                                col = ref.fold(0) { acc, ch -> acc * 26 + (ch.uppercaseChar() - 'A' + 1) } - 1
                                value = StringBuilder()
                            }
                            "v" -> inV = true
                            "is" -> inIs = true
                            "t" -> if (inIs) inV = true
                        }
                        XmlPullParser.TEXT -> if (inV) value?.append(p.text)
                        XmlPullParser.END_TAG -> when (p.name) {
                            "v" -> inV = false
                            "t" -> if (inIs) inV = false
                            "is" -> inIs = false
                            "c" -> {
                                val r = row
                                if (r != null && col >= 0) {
                                    while (r.size < col) r.add("")
                                    val raw = value?.toString().orEmpty()
                                    r.add(if (type == "s") shared.getOrElse(raw.trim().toIntOrNull() ?: -1) { "" } else raw)
                                }
                            }
                            "row" -> { row?.let { rows.add(it) }; row = null }
                        }
                    }
                    ev = p.next()
                }
                if (rows.isNotEmpty()) sheets.add(rows)
            }
            if (sheets.isEmpty()) throw ToolException("No data found in this spreadsheet.")
            return sheets
        }
    }

    // ---------------------------------------------------------------- PDF drawing

    /** Writes styled text across as many A4 pages as it needs. */
    fun spannedToPdf(text: CharSequence, out: File) {
        val pageW = 595
        val pageH = 842
        val margin = 42
        val textW = pageW - 2 * margin
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.BLACK }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, textW)
            .setLineSpacing(0f, 1.15f).setIncludePad(false).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        val doc = PdfDocument()
        try {
            var line = 0
            var pageNo = 1
            val usable = pageH - 2 * margin
            do {
                val top = layout.getLineTop(line)
                var end = line
                while (end < layout.lineCount && layout.getLineBottom(end) - top <= usable) end++
                if (end == line) end = line + 1
                val bottom = layout.getLineBottom(end - 1)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
                val canvas = page.canvas
                canvas.save()
                canvas.translate(margin.toFloat(), (margin - top).toFloat())
                canvas.clipRect(0f, top.toFloat(), textW.toFloat(), bottom.toFloat())
                layout.draw(canvas)
                canvas.restore()
                doc.finishPage(page)
                line = end
                pageNo++
            } while (line < layout.lineCount)
            FileOutputStream(out).use { doc.writeTo(it) }
        } finally { doc.close() }
    }

    fun htmlToSpanned(html: String): Spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)

    /** Draws sheets as simple tables on landscape A4 pages. */
    fun sheetsToPdf(sheets: List<List<List<String>>>, out: File) {
        val pageW = 842
        val pageH = 595
        val margin = 30
        val usableW = pageW - 2 * margin
        val doc = PdfDocument()
        try {
            var pageNo = 1
            for (rows in sheets) {
                val cols = rows.maxOf { it.size }.coerceAtLeast(1)
                var size = 9f
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; color = Color.BLACK }
                fun widths(): FloatArray = FloatArray(cols) { c ->
                    minOf(180f, (rows.take(300).maxOfOrNull { r -> paint.measureText(r.getOrElse(c) { "" }) } ?: 0f) + 10f).coerceAtLeast(24f)
                }
                var w = widths()
                while (w.sum() > usableW && size > 5f) { size -= 0.5f; paint.textSize = size; w = widths() }
                if (w.sum() > usableW) { val k = usableW / w.sum(); for (i in w.indices) w[i] *= k }
                val rowH = size + 8f
                val grid = Paint().apply { color = Color.rgb(200, 200, 200); strokeWidth = 0.5f }
                val perPage = ((pageH - 2 * margin) / rowH).toInt().coerceAtLeast(1)
                rows.chunked(perPage).forEach { chunk ->
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo++).create())
                    val cv = page.canvas
                    var y = margin.toFloat()
                    chunk.forEach { r ->
                        var x = margin.toFloat()
                        for (c in 0 until cols) {
                            var t = r.getOrElse(c) { "" }
                            val fit = paint.breakText(t, true, w[c] - 6f, null)
                            if (fit < t.length) t = t.substring(0, fit.coerceAtLeast(0))
                            cv.drawText(t, x + 3f, y + size + 2f, paint)
                            cv.drawLine(x, y, x, y + rowH, grid)
                            x += w[c]
                        }
                        cv.drawLine(x, y, x, y + rowH, grid)
                        cv.drawLine(margin.toFloat(), y + rowH, x, y + rowH, grid)
                        y += rowH
                    }
                    doc.finishPage(page)
                }
            }
            FileOutputStream(out).use { doc.writeTo(it) }
        } finally { doc.close() }
    }

    // ---------------------------------------------------------------- compression

    /** Shrinks a .docx / .xlsx / .pptx by re-packing it and re-compressing the pictures inside. [level] 0 = smallest. */
    fun compressOffice(tc: ToolContext, src: File, out: File, level: Int) {
        val maxEdge = listOf(1024, 1600, 2400)[level]
        val quality = listOf(50, 65, 80)[level]
        val imgExt = setOf("jpg", "jpeg", "png")
        val zin = try { ZipFile(src) } catch (e: Exception) { throw ToolException("This isn't a .docx, .xlsx or .pptx file.") }
        zin.use { z ->
            ZipOutputStream(FileOutputStream(out)).use { zo ->
                zo.setLevel(Deflater.BEST_COMPRESSION)
                val entries = z.entries().toList()
                entries.forEachIndexed { i, e ->
                    tc.checkCancelled()
                    tc.progress(i / entries.size.toFloat(), "Packing ${i + 1} of ${entries.size}")
                    if (e.isDirectory) return@forEachIndexed
                    val bytes = z.getInputStream(e).use { it.readBytes() }
                    val ext = e.name.substringAfterLast('.', "").lowercase()
                    var data = bytes
                    if (ext in imgExt && e.name.contains("/media/")) {
                        try {
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) {
                                val scaled = ImageEngine.scaleDown(bmp, maxEdge)
                                val bos = ByteArrayOutputStream()
                                if (ext == "png") scaled.compress(Bitmap.CompressFormat.PNG, 100, bos)
                                else scaled.compress(Bitmap.CompressFormat.JPEG, quality, bos)
                                if (bos.size() < bytes.size) data = bos.toByteArray()
                                scaled.recycle()
                            }
                        } catch (_: Throwable) { }
                    }
                    zo.putNextEntry(ZipEntry(e.name))
                    zo.write(data)
                    zo.closeEntry()
                }
            }
        }
    }
}
