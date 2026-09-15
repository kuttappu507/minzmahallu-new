package com.mms.minzmahallu.util

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File

/**
 * Monochrome PDF builder (pdfbox-android) for receipts, registers and certificates.
 * Only long-stable PDFBox APIs are used so minor library updates cannot break the build.
 */
object PdfUtil {

    private var inited = false

    fun ensureInit(ctx: Context) {
        if (!inited) {
            PDFBoxResourceLoader.init(ctx.applicationContext)
            inited = true
        }
    }

    /** Prefer a system Unicode font (Malayalam-capable) so names print correctly. */
    private fun unicodeFont(doc: PDDocument): PDFont {
        val candidates = listOf(
            "/system/fonts/NotoSansMalayalam-Regular.ttf",
            "/system/fonts/NotoSansMalayalam-Bold.ttf",
            "/system/fonts/NotoSansMalayalamUI-Regular.ttf",
            "/system/fonts/NotoSans-Regular.ttf",
            "/system/fonts/DroidSansFallback.ttf",
            "/system/fonts/Roboto-Regular.ttf"
        )
        for (p in candidates) {
            try {
                val f = File(p)
                if (f.exists() && f.length() > 0) return PDType0Font.load(doc, f)
            } catch (_: Exception) {
            }
        }
        return PDType1Font.HELVETICA
    }

    class Doc(ctx: Context) {
        val doc: PDDocument = PDDocument()
        val font: PDFont
        private var page: PDPage = PDPage(PDRectangle.A4)
        private var cs: PDPageContentStream
        private var y = 0f
        private val margin = 46f
        private val bottom = 60f
        val pageWidth: Float get() = page.mediaBox.width

        init {
            PdfUtil.ensureInit(ctx)
            font = PdfUtil.unicodeFont(doc)
            doc.addPage(page)
            cs = PDPageContentStream(doc, page)
            y = page.mediaBox.height - margin
        }

        fun need(h: Float) {
            if (y - h < bottom) {
                try { cs.close() } catch (_: Exception) { }
                page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                cs = PDPageContentStream(doc, page)
                y = page.mediaBox.height - margin
            }
        }

        private fun show(s: String) {
            try {
                cs.showText(s)
            } catch (_: Exception) {
                try {
                    cs.showText(s.replace(Regex("[^\\x20-\\x7E]"), "?"))
                } catch (_: Exception) {
                }
            }
        }

        fun text(s: String, size: Float, x: Float = margin, gap: Float = 6f) {
            need(size + 10f)
            cs.beginText()
            cs.setFont(font, size)
            cs.newLineAtOffset(x, y)
            show(s)
            cs.endText()
            y -= (size + gap)
        }

        fun centered(s: String, size: Float, gap: Float = 6f) {
            need(size + 10f)
            val w = try {
                font.getStringWidth(s) / 1000f * size
            } catch (_: Exception) {
                s.length * size * 0.55f
            }
            val x = ((pageWidth - w) / 2f).coerceAtLeast(margin)
            text(s, size, x, gap)
        }

        fun rule(gapBefore: Float = 4f, gapAfter: Float = 10f) {
            y -= gapBefore
            need(10f)
            cs.moveTo(margin, y)
            cs.lineTo(pageWidth - margin, y)
            cs.stroke()
            y -= gapAfter
        }

        fun blank(h: Float = 10f) {
            y -= h
        }

        fun keyValue(k: String, v: String, keyWidth: Float = 150f) {
            need(20f)
            cs.beginText()
            cs.setFont(font, 10f)
            cs.newLineAtOffset(margin, y)
            show(k)
            cs.endText()
            cs.beginText()
            cs.setFont(font, 10f)
            cs.newLineAtOffset(margin + keyWidth, y)
            show(v)
            cs.endText()
            y -= 16f
        }

        fun tableRow(cells: List<String>, weights: List<Float>, size: Float = 9.5f, header: Boolean = false) {
            need(22f)
            val maxW = pageWidth - 2 * margin
            val total = weights.sum().coerceAtLeast(1f)
            var x = margin
            cells.forEachIndexed { i, c ->
                val w = (weights.getOrElse(i) { 1f } / total) * maxW
                val maxChars = ((w / (size * 0.58f)).toInt()).coerceAtLeast(4)
                val s = if (c.length > maxChars) c.take(maxChars - 1) + "…" else c
                cs.beginText()
                cs.setFont(font, size)
                cs.newLineAtOffset(x + 4f, y)
                show(s)
                cs.endText()
                x += w
            }
            y -= 16f
            if (header) {
                cs.moveTo(margin, y + 5f)
                cs.lineTo(pageWidth - margin, y + 5f)
                cs.stroke()
            }
        }

        fun border() {
            cs.addRect(margin - 12f, bottom - 16f, pageWidth - 2 * (margin - 12f), page.mediaBox.height - margin - bottom + 28f)
            cs.stroke()
        }

        fun finish(file: File): File {
            try { cs.close() } catch (_: Exception) { }
            file.parentFile?.mkdirs()
            doc.save(file)
            try { doc.close() } catch (_: Exception) { }
            return file
        }
    }

    // ------------------------------------------------------------------ builders

    fun receiptPdf(
        ctx: Context,
        title: String,
        orgLines: List<String>,
        rows: List<Pair<String, String>>,
        footer: String?,
        fileName: String
    ): File {
        val d = Doc(ctx)
        orgLines.forEachIndexed { i, line ->
            if (i == 0) d.centered(line, 17f, 4f) else d.centered(line, 9.5f, 3f)
        }
        d.rule()
        d.centered(title, 14f, 4f)
        d.blank(4f)
        rows.forEach { (k, v) -> d.keyValue(k, v) }
        d.rule(10f)
        d.text("Generated by Minz Mahallu Management System  |  ${Format.today()}", 8f)
        if (!footer.isNullOrBlank()) d.text(footer, 8.5f)
        return d.finish(ShareUtil.cacheFile(ctx, fileName))
    }

    fun tablePdf(
        ctx: Context,
        title: String,
        subtitle: String,
        orgName: String,
        columns: List<String>,
        weights: List<Float>?,
        rows: List<List<String>>,
        footer: String?,
        fileName: String
    ): File {
        val d = Doc(ctx)
        d.centered(orgName, 16f, 3f)
        d.centered(title, 12.5f, 2f)
        if (subtitle.isNotBlank()) d.centered(subtitle, 9.5f, 2f)
        d.rule()
        val w = weights ?: columns.map { 1f }
        d.tableRow(columns, w, header = true)
        rows.forEach { r -> d.tableRow(r, w) }
        d.rule(10f)
        d.text("Total rows: ${rows.size}   |   Printed ${Format.today()}", 8.5f)
        if (!footer.isNullOrBlank()) d.text(footer, 8.5f)
        return d.finish(ShareUtil.cacheFile(ctx, fileName))
    }

    fun certificatePdf(
        ctx: Context,
        orgName: String,
        orgSub: String,
        certTitle: String,
        issuedTo: String,
        certNo: String,
        verifyCode: String,
        dateText: String,
        extra: List<Pair<String, String>>,
        fileName: String
    ): File {
        val d = Doc(ctx)
        d.border()
        d.blank(26f)
        d.centered(orgName, 20f, 3f)
        if (orgSub.isNotBlank()) d.centered(orgSub, 10f, 2f)
        d.rule(8f, 14f)
        d.centered(certTitle, 16f, 6f)
        d.centered("This is to certify that", 10.5f, 6f)
        d.centered(issuedTo, 15f, 10f)
        extra.forEach { (k, v) ->
            d.centered("$k: $v", 10f, 4f)
        }
        d.blank(18f)
        d.keyValue("Certificate No", certNo)
        d.keyValue("Issued Date", dateText)
        d.keyValue("Verification Code", verifyCode)
        d.blank(26f)
        val left = 46f
        val right = d.pageWidth - 46f - 150f
        d.text("Seal & Signature", 10f, right, 0f)
        d.text("Verify: $verifyCode", 9f, left, 0f)
        d.blank(8f)
        d.text("Generated by Minz Mahallu Management System", 8f, left, 0f)
        return d.finish(ShareUtil.cacheFile(ctx, fileName))
    }
}
