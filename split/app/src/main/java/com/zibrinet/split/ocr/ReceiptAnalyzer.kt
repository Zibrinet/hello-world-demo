package com.zibrinet.split.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class AnalyzedReceipt(
    val imagePath: String,
    val rawText: String?,
    val parsed: ParsedReceipt,
)

/**
 * Capture-side pipeline: copy the picked/scanned image into app-private
 * storage, run on-device ML Kit text recognition, then apply the parsing
 * heuristics. OCR failure is never fatal — the image still attaches.
 */
class ReceiptAnalyzer(private val appContext: Context) {

    suspend fun analyze(source: Uri, homeCurrency: String): AnalyzedReceipt =
        withContext(Dispatchers.IO) {
            val imagePath = copyToPrivateStorage(source)
            val rawText = try {
                val image = InputImage.fromFilePath(appContext, Uri.fromFile(File(imagePath)))
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    recognizer.process(image).await().text.takeIf { it.isNotBlank() }
                } finally {
                    recognizer.close()
                }
            } catch (_: Exception) {
                null
            }
            AnalyzedReceipt(
                imagePath = imagePath,
                rawText = rawText,
                parsed = rawText?.let { ReceiptParser.parse(it, homeCurrency) } ?: ParsedReceipt(),
            )
        }

    private fun copyToPrivateStorage(source: Uri): String {
        val dir = File(appContext.filesDir, "receipts").apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}.jpg")
        appContext.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Could not open image $source" }
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target.absolutePath
    }
}
