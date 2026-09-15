package com.mms.minzmahallu.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** System share-sheet + WhatsApp deep-link helpers (no SDK, plain ACTION_VIEW intents). */
object ShareUtil {

    fun shareText(ctx: Context, text: String, title: String = "Share") {
        try {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            ctx.startActivity(Intent.createChooser(i, title))
        } catch (e: Exception) {
            Toast.makeText(ctx, e.message ?: "Share failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(ctx: Context, file: File, mime: String, title: String = "Share") {
        try {
            val uri: Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(i, title))
        } catch (e: Exception) {
            Toast.makeText(ctx, e.message ?: "Share failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun viewPdf(ctx: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(i, "Open PDF"))
        } catch (e: Exception) {
            Toast.makeText(ctx, e.message ?: "No PDF viewer found", Toast.LENGTH_SHORT).show()
        }
    }

    /** Normalise to wa.me international format (defaults to India 91). Null when unusable. */
    fun normalizePhone(raw: String): String? {
        var d = raw.filter { it.isDigit() }
        if (d.isEmpty()) return null
        if (d.length == 12 && d.startsWith("91")) return d
        if (d.length == 11 && d.startsWith("0")) d = d.drop(1)
        if (d.length == 10 && d[0] in '6'..'9') return "91$d"
        if (d.length in 10..15) return d
        return null
    }

    fun prettyPhone(raw: String): String {
        val d = raw.filter { it.isDigit() }
        if (d.isEmpty()) return ""
        return if (d.length == 10) "${d.take(5)} ${d.drop(5)}" else raw.trim()
    }

    /**
     * Open a WhatsApp chat with prefilled [text]. Prefers the WhatsApp app,
     * then WhatsApp Business, then the browser, then the generic share sheet.
     */
    fun openWhatsApp(ctx: Context, phoneRaw: String, text: String): Boolean {
        val phone = normalizePhone(phoneRaw)
        if (phone == null) {
            Toast.makeText(ctx, "No valid phone number", Toast.LENGTH_SHORT).show()
            return false
        }
        val url = "https://wa.me/$phone?text=" + Uri.encode(text)
        val uri = Uri.parse(url)
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.whatsapp") })
            return true
        } catch (_: Exception) { }
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.whatsapp.w4b") })
            return true
        } catch (_: Exception) { }
        return try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: Exception) {
            shareText(ctx, text, "Send via…")
            true
        }
    }

    /** Private cache dir for generated PDFs (served through FileProvider). */
    fun cacheFile(ctx: Context, name: String): File {
        val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
        return File(dir, name.filter { it.isLetterOrDigit() || it in "._-()" })
    }

    fun dial(ctx: Context, phoneRaw: String) {
        val d = phoneRaw.filter { it.isDigit() || it == '+' }
        if (d.isBlank()) {
            Toast.makeText(ctx, "No phone number", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$d")))
        } catch (e: Exception) {
            Toast.makeText(ctx, e.message ?: "Cannot dial", Toast.LENGTH_SHORT).show()
        }
    }
}
