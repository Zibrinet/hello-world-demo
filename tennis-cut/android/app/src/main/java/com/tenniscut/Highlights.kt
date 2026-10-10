package com.tenniscut

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val HIGHLIGHT_BITRATE = 8_000_000 // about 3.5 GB per hour of highlights at 1080p

data class VideoInfo(val durationS: Double, val height: Int)

fun videoInfo(video: File): VideoInfo {
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(video.absolutePath)
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
        val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        return VideoInfo(ms / 1000.0, minOf(w, h)) // recorded in landscape: the short side is the height
    } finally {
        r.release()
    }
}

/** Cuts every point out of the match, burns in the score, saves one video to the gallery. */
suspend fun makeHighlights(context: Context, session: SessionDir, onProgress: (Int) -> Unit): Uri =
    withContext(Dispatchers.Main) {
        val setup = session.readSetup()
        val info = videoInfo(session.video)
        val points = pointsFrom(setup, resolveMarkers(session.readMarkers()), info.durationS)
        require(points.isNotEmpty()) { "No points were marked in this match." }

        val items = points.map { p ->
            val clip = MediaItem.Builder()
                .setUri(Uri.fromFile(session.video))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs((p.start * 1000).toLong())
                        .setEndPositionMs((p.end * 1000).toLong())
                        .build()
                )
                .build()
            val board = BitmapOverlay.createStaticBitmapOverlay(
                scoreboardBitmap(setup, p.before, info.height),
                StaticOverlaySettings.Builder()
                    .setOverlayFrameAnchor(-1f, 1f) // the board's top-left corner...
                    .setBackgroundFrameAnchor(-0.97f, 0.94f) // ...sits near the video's top-left corner
                    .build(),
            )
            EditedMediaItem.Builder(clip)
                .setEffects(Effects(listOf(), listOf(OverlayEffect(ImmutableList.of(board)))))
                .build()
        }
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(items).build()).build()
        val out = File(session.dir, "highlights.mp4").also { it.delete() }
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder().setBitrate(HIGHLIGHT_BITRATE).build()
                    )
                    .build()
            )
            .build()

        coroutineScope {
            val progress = launch {
                val holder = ProgressHolder()
                while (true) {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress)
                    }
                    delay(500)
                }
            }
            try {
                suspendCancellableCoroutine { cont ->
                    transformer.addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            cont.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            cont.resumeWithException(exportException)
                        }
                    })
                    transformer.start(composition, out.absolutePath)
                    cont.invokeOnCancellation { transformer.cancel() }
                }
            } finally {
                progress.cancel()
            }
        }

        val uri = withContext(Dispatchers.IO) {
            saveToGallery(context, out, "Tennis ${setup.sessionId}.mp4").also { out.delete() }
        }
        session.highlightsUri = uri
        uri
    }

private fun saveToGallery(context: Context, file: File, displayName: String): Uri {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Tennis Cut")
        put(MediaStore.Video.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("Could not save the video to the gallery.")
    resolver.openOutputStream(uri)!!.use { output -> file.inputStream().use { it.copyTo(output) } }
    resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
    return uri
}

/** Score as it stands before a point, drawn at the size it should appear on a video this tall. */
fun scoreboardBitmap(setup: Setup, s: Score, videoHeight: Int): Bitmap {
    val u = videoHeight / 1080f
    val pad = 14 * u
    val rowH = 52 * u
    val dotW = 28 * u
    val nameW = 250 * u
    val colW = 50 * u
    val ptsW = 76 * u
    val columns = s.sets + if (Tennis.isMatchTiebreak(s, setup.format)) emptyList() else listOf(s.games)
    val labels = Tennis.pointLabels(s, setup.format)
    val serving = Tennis.server(s, setup.format)

    val width = 2 * pad + dotW + nameW + colW * columns.size + ptsW
    val height = 2 * pad + 2 * rowH
    val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawRoundRect(0f, 0f, width, height, 10 * u, 10 * u, Paint().apply { color = Color.argb(180, 0, 0, 0) })

    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 38 * u
        typeface = Typeface.DEFAULT_BOLD
    }
    val yellow = Paint(white).apply { color = Color.rgb(255, 229, 0) }
    for (player in Player.entries) {
        val row = player.ordinal
        val centerY = pad + rowH * (row + 0.5f)
        val baseline = centerY + white.textSize * 0.35f
        var x = pad
        if (player == serving) canvas.drawCircle(x + dotW / 2, centerY, 7 * u, yellow)
        x += dotW
        canvas.drawText(setup.name(player).take(14), x, baseline, white.apply { textAlign = Paint.Align.LEFT })
        x += nameW
        for (col in columns) {
            x += colW
            canvas.drawText("${col[row]}", x, baseline, white.apply { textAlign = Paint.Align.RIGHT })
        }
        x += ptsW
        canvas.drawText(labels[row], x, baseline, yellow.apply { textAlign = Paint.Align.RIGHT })
    }
    return bitmap
}
