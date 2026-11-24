package com.linkvault.app.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.io.FileOutputStream

object PdfEngine {
    fun init(context: Context) { PDFBoxResourceLoader.init(context.applicationContext) }

    private fun openRenderer(file: File): Pair<PdfRenderer, ParcelFileDescriptor> {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            return PdfRenderer(pfd) to pfd
        } catch (e: SecurityException) {
            pfd.close()
            throw ToolException("This PDF is locked. Open Tools → Unlock first, then try again.")
        } catch (e: Exception) {
            pfd.close()
            throw ToolException("This PDF can't be opened. It may be damaged.")
        }
    }

    private fun renderPage(page: PdfRenderer.Page, scale: Float): Bitmap {
        var s = scale
        val longest = maxOf(page.width, page.height) * s
        if (longest > 3200f) s *= 3200f / longest
        val bmp = Bitmap.createBitmap((page.width * s).toInt().coerceAtLeast(1), (page.height * s).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bmp
    }

    /** PDF to PNG or JPG: one picture per page. */
    fun pdfToImages(tc: ToolContext, file: File, base: String, png: Boolean): List<OutFile> {
        val (renderer, pfd) = openRenderer(file)
        val outs = ArrayList<OutFile>()
        try {
            val n = renderer.pageCount
            for (i in 0 until n) {
                tc.checkCancelled()
                tc.progress(i / n.toFloat(), "Page ${i + 1} of $n")
                val page = renderer.openPage(i)
                val bmp = try { renderPage(page, 2f) } finally { page.close() }
                val out = tc.tempFile("${base.take(70)}_page${i + 1}.${if (png) "png" else "jpg"}")
                FileOutputStream(out).use { os ->
                    bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, os)
                }
                bmp.recycle()
                outs.add(OutFile(out))
            }
        } finally { renderer.close(); pfd.close() }
        return outs
    }

    /** JPG/PNG pictures to one PDF, a page per picture. */
    fun imagesToPdf(tc: ToolContext, images: List<PickedFile>, name: String): OutFile {
        init(tc.app)
        val doc = PDDocument()
        try {
            images.forEachIndexed { i, p ->
                tc.checkCancelled()
                tc.progress(i / images.size.toFloat(), "Picture ${i + 1} of ${images.size}")
                val bmp = ImageEngine.decode(tc.app, p.uri, 2400)
                try {
                    val w = 595f
                    val h = (w * bmp.height / bmp.width).coerceIn(150f, 1700f)
                    val page = PDPage(PDRectangle(w, h))
                    doc.addPage(page)
                    val img = JPEGFactory.createFromImage(doc, if (bmp.hasAlpha()) flattenWhite(bmp) else bmp, 0.85f)
                    PDPageContentStream(doc, page).use { cs -> cs.drawImage(img, 0f, 0f, w, h) }
                } finally { bmp.recycle() }
            }
            val out = tc.tempFile("${name.take(80)}.pdf")
            doc.save(out)
            return OutFile(out)
        } finally { doc.close() }
    }

    private fun flattenWhite(bmp: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(bmp, 0f, 0f, null)
        return out
    }

    /** Makes a PDF smaller by redrawing every page as a compressed picture (text turns into picture). */
    fun compressPdf(tc: ToolContext, file: File, base: String, level: Int): OutFile {
        init(tc.app)
        val scale = listOf(1.0f, 1.5f, 2.0f)[level]
        val quality = listOf(0.5f, 0.65f, 0.8f)[level]
        val (renderer, pfd) = openRenderer(file)
        val doc = PDDocument()
        try {
            val n = renderer.pageCount
            for (i in 0 until n) {
                tc.checkCancelled()
                tc.progress(i / n.toFloat(), "Page ${i + 1} of $n")
                val page = renderer.openPage(i)
                val pw = page.width.toFloat()
                val ph = page.height.toFloat()
                val bmp = try { renderPage(page, scale) } finally { page.close() }
                val pdPage = PDPage(PDRectangle(pw, ph))
                doc.addPage(pdPage)
                val img = JPEGFactory.createFromImage(doc, bmp, quality)
                PDPageContentStream(doc, pdPage).use { cs -> cs.drawImage(img, 0f, 0f, pw, ph) }
                bmp.recycle()
            }
            val out = tc.tempFile("${base.take(80)}_compressed.pdf")
            doc.save(out)
            if (out.length() >= file.length()) {
                throw ToolException("This PDF is already small (mostly text), so compressing would make it bigger.")
            }
            return OutFile(out)
        } finally { renderer.close(); pfd.close(); doc.close() }
    }

    /** Text of every page, for the Word / Excel / HTML conversions. */
    fun extractPages(tc: ToolContext, file: File, password: String = ""): List<String> {
        init(tc.app)
        val doc = try { PDDocument.load(file, password) } catch (e: InvalidPasswordException) {
            throw ToolException("This PDF is locked. Open Tools → Unlock first, then try again.")
        } catch (e: Exception) { throw ToolException("This PDF can't be read. It may be damaged.") }
        try {
            val stripper = PDFTextStripper()
            stripper.setSortByPosition(true)
            val n = doc.numberOfPages
            val pages = ArrayList<String>()
            for (i in 1..n) {
                tc.checkCancelled()
                tc.progress((i - 1) / n.toFloat(), "Reading page $i of $n")
                stripper.setStartPage(i)
                stripper.setEndPage(i)
                pages.add(stripper.getText(doc))
            }
            if (pages.all { it.isBlank() }) {
                throw ToolException("No text found. This looks like a scanned PDF (pictures only), so it can't be turned into text.")
            }
            return pages
        } finally { doc.close() }
    }

    /** Removes the password using the password the person types. */
    fun unlockPdf(tc: ToolContext, file: File, password: String, base: String): OutFile {
        init(tc.app)
        val doc = try { PDDocument.load(file, password) } catch (e: InvalidPasswordException) {
            throw ToolException("Wrong password. Check it and try again.")
        } catch (e: Exception) { throw ToolException("This PDF can't be opened. It may be damaged.") }
        try {
            if (!doc.isEncrypted) throw ToolException("This PDF isn't locked.")
            doc.setAllSecurityToBeRemoved(true)
            val out = tc.tempFile("${base.take(80)}_unlocked.pdf")
            doc.save(out)
            return OutFile(out)
        } finally { doc.close() }
    }

    /** Puts a password on a PDF. */
    fun protectPdf(tc: ToolContext, file: File, password: String, base: String): OutFile {
        init(tc.app)
        val doc = try { PDDocument.load(file) } catch (e: InvalidPasswordException) {
            throw ToolException("This PDF already has a password.")
        } catch (e: Exception) { throw ToolException("This PDF can't be opened. It may be damaged.") }
        try {
            if (doc.isEncrypted) throw ToolException("This PDF already has a password.")
            val policy = StandardProtectionPolicy(password, password, AccessPermission())
            policy.setEncryptionKeyLength(128)
            policy.setPreferAES(true)
            doc.protect(policy)
            val out = tc.tempFile("${base.take(80)}_protected.pdf")
            doc.save(out)
            return OutFile(out)
        } finally { doc.close() }
    }

    /** Any other files: one password-protected ZIP (AES-256). */
    fun protectAsZip(tc: ToolContext, files: List<Pair<File, String>>, password: String, base: String): OutFile {
        val out = tc.tempFile("${base.take(60)}_protected.zip")
        val zip = ZipFile(out, password.toCharArray())
        files.forEachIndexed { i, (f, name) ->
            tc.checkCancelled()
            tc.progress(i / files.size.toFloat(), "Locking ${i + 1} of ${files.size}")
            val params = ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                fileNameInZip = name
            }
            zip.addFile(f, params)
        }
        return OutFile(out)
    }

    /** Opens a ZIP made by Protect. */
    fun unlockZip(tc: ToolContext, file: File, password: String): List<OutFile> {
        val dir = tc.tempDir("unzip")
        try {
            val zip = ZipFile(file, password.toCharArray())
            if (!zip.isValidZipFile) throw ToolException("This isn't a valid ZIP file.")
            if (!zip.isEncrypted) throw ToolException("This ZIP isn't locked.")
            zip.extractAll(dir.absolutePath)
        } catch (e: ToolException) { throw e
        } catch (e: Exception) {
            throw ToolException(if (e.message.orEmpty().contains("password", true)) "Wrong password. Check it and try again." else "Couldn't open this ZIP.")
        }
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty()) throw ToolException("The ZIP is empty.")
        return files.map { OutFile(it) }
    }
}
