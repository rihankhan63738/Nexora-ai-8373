package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.model.AttachmentPayload
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportAndShareUtils {

    private const val MAX_READ_BYTES = 18 * 1024 * 1024

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun shareText(context: Context, title: String, body: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(intent, "Share via"))
    }

    fun formatConversationTranscript(conversation: Conversation, messages: List<ChatMessage>): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return buildString {
            appendLine("NEXORA AI — CONVERSATION EXPORT")
            appendLine("Title: ${conversation.title}")
            appendLine("Model: ${conversation.modelId}")
            appendLine("Exported: ${sdf.format(Date())}")
            appendLine("=".repeat(48))
            appendLine()
            messages.forEach { msg ->
                val sender = if (msg.role == "user") "YOU" else "NEXORA AI (${msg.modelId ?: conversation.modelId})"
                val ts = msg.createdAt?.toDate()?.let { sdf.format(it) } ?: ""
                appendLine("[$sender] $ts")
                if (!msg.attachmentName.isNullOrBlank()) {
                    appendLine("(Attachment: ${msg.attachmentName})")
                }
                appendLine(msg.content)
                if (msg.sources.isNotEmpty()) {
                    appendLine("Sources:")
                    msg.sources.forEach { appendLine("  - $it") }
                }
                appendLine()
                appendLine("-".repeat(36))
                appendLine()
            }
        }
    }

    fun exportConversationToTxtAndShare(
        context: Context,
        conversation: Conversation,
        messages: List<ChatMessage>
    ): Result<File> = runCatching {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = conversation.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(36).ifBlank { "nexora_chat" }
        val file = File(exportDir, "${safeName}_${System.currentTimeMillis()}.txt")
        file.writeText(formatConversationTranscript(conversation, messages), Charsets.UTF_8)
        shareFile(context, file, "text/plain", "Export Conversation (TXT)")
        file
    }

    fun exportConversationToPdfAndShare(
        context: Context,
        conversation: Conversation,
        messages: List<ChatMessage>
    ): Result<File> = runCatching {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = conversation.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(36).ifBlank { "nexora_chat" }
        val file = File(exportDir, "${safeName}_${System.currentTimeMillis()}.pdf")

        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 42f
        val maxLineWidth = pageWidth - margin * 2

        val titlePaint = Paint().apply {
            color = Color.rgb(12, 74, 110)
            textSize = 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val metaPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 10f
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11f
            isAntiAlias = true
        }

        val transcriptLines = wrapLines(
            formatConversationTranscript(conversation, messages),
            bodyPaint,
            maxLineWidth
        )

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdf.startPage(pageInfo)
        var canvas = page.canvas
        var y = margin + 16f

        canvas.drawText("Nexora AI — ${conversation.title.take(45)}", margin, y, titlePaint)
        y += 18f
        canvas.drawText("Model: ${conversation.modelId} · Exported from Nexora AI", margin, y, metaPaint)
        y += 22f

        for (line in transcriptLines) {
            if (y > pageHeight - margin) {
                pdf.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdf.startPage(pageInfo)
                canvas = page.canvas
                y = margin + 14f
            }
            canvas.drawText(line, margin, y, bodyPaint)
            y += 15f
        }

        pdf.finishPage(page)
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()

        shareFile(context, file, "application/pdf", "Export Conversation (PDF)")
        file
    }

    private fun wrapLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val output = mutableListOf<String>()
        text.lines().forEach { rawLine ->
            if (rawLine.isEmpty()) {
                output.add("")
                return@forEach
            }
            var remaining = rawLine
            while (remaining.isNotEmpty()) {
                val count = paint.breakText(remaining, true, maxWidth, null)
                if (count <= 0) break
                output.add(remaining.substring(0, count))
                remaining = remaining.substring(count)
            }
        }
        return output
    }

    fun saveGeneratedBitmapInternally(context: Context, bitmap: Bitmap): File {
        val dir = File(context.filesDir, "generated_images").apply { mkdirs() }
        val file = File(dir, "nexora_img_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    fun saveImageToGallery(context: Context, bitmap: Bitmap, title: String): Result<String> = runCatching {
        val filename = "Nexora_${title.replace(Regex("[^a-zA-Z0-9]"), "_").take(24)}_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NexoraAI")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create MediaStore entry")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: error("Could not open image output stream")
            "Saved to Pictures/NexoraAI/$filename"
        } else {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, filename)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            "Saved to ${file.name}"
        }
    }

    fun shareImageFile(context: Context, file: File) {
        shareFile(context, file, "image/png", "Share Generated Image")
    }

    private fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun readAttachmentFromUri(context: Context, uri: Uri): Result<AttachmentPayload> = runCatching {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        var displayName = "attachment"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) {
                    displayName = cursor.getString(nameIdx) ?: displayName
                }
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    val size = cursor.getLong(sizeIdx)
                    if (size > MAX_READ_BYTES) {
                        error("File is too large (${size / (1024 * 1024)}MB). Maximum supported size is 18MB.")
                    }
                }
            }
        }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read selected file.")
        if (bytes.size > MAX_READ_BYTES) {
            error("File exceeds 18MB inline size limit.")
        }
        AttachmentPayload(
            name = displayName,
            mimeType = mime,
            bytes = bytes,
            previewUri = uri.toString()
        )
    }
}
