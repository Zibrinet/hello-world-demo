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

enum class CaptureMode {
    /** Pages of one document (document scanner): parse as a single receipt. */
    SCAN_PAGES,

    /** Independent photos (gallery multi-pick): one receipt each, totals sum. */
    SEPARATE_PHOTOS,
}

data class AnalyzedBatch(
    val imagePaths: List<String>,
    val rawText: String?,
    val parsed: ParsedReceipt,
    /** Per-image parse results, index-aligned with [imagePaths]. */
    val perImage: List<ParsedReceipt>,
    val amountsFound: Int,
    val mixedCurrencies: Boolean,
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
                    val visionText = recognizer.process(image).await()
                    // Rebuild visual rows so a "TOTAL" label rejoins the number
                    // that sat beside it on the receipt (block order splits them).
                    val ocrLines = visionText.textBlocks
                        .flatMap { it.lines }
                        .mapNotNull { line ->
                            line.boundingBox?.let { box ->
                                OcrLine(line.text, box.left, box.centerY(), box.height())
                            }
                        }
                    reconstructRows(ocrLines).ifBlank { visionText.text }
                        .takeIf { it.isNotBlank() }
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

    /** Multi-image capture; combining semantics depend on [mode]. */
    suspend fun analyzeAll(
        sources: List<Uri>,
        homeCurrency: String,
        mode: CaptureMode,
    ): AnalyzedBatch {
        val results = sources.map { analyze(it, homeCurrency) }
        val rawText = results.mapNotNull { it.rawText }
            .joinToString("\n----\n").ifBlank { null }

        return when (mode) {
            CaptureMode.SCAN_PAGES -> {
                // One receipt spread over pages: parse the joined text so the
                // grand total wins wherever it appears, never a per-page sum.
                val joined = results.mapNotNull { it.rawText }.joinToString("\n")
                val parsed = if (joined.isBlank()) {
                    ParsedReceipt()
                } else {
                    ReceiptParser.parse(joined, homeCurrency)
                }
                AnalyzedBatch(
                    imagePaths = results.map { it.imagePath },
                    rawText = rawText,
                    parsed = parsed,
                    perImage = results.map { ParsedReceipt() },
                    amountsFound = if (parsed.amountMinor != null) sources.size else 0,
                    mixedCurrencies = false,
                )
            }

            CaptureMode.SEPARATE_PHOTOS -> {
                val merged = BatchMerge.mergeSeparatePhotos(results.map { it.parsed })
                AnalyzedBatch(
                    imagePaths = results.map { it.imagePath },
                    rawText = rawText,
                    parsed = merged.parsed,
                    perImage = results.map { it.parsed },
                    amountsFound = merged.amountsFound,
                    mixedCurrencies = merged.mixedCurrencies,
                )
            }
        }
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
