package com.tenniscut

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
import androidx.media3.effect.TextureOverlay
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

private const val EXPORT_BITRATE = 8_000_000 // about 3.5 GB per hour of output at 1080p
const val TOP_RALLY_COUNT = 10

/** `shortSide` is the frame's short side as shown (1080 for 1080p, whichever way up). */
data class VideoInfo(val durationS: Double, val shortSide: Int)

fun videoInfo(video: File): VideoInfo {
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(video.absolutePath)
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
        val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        return VideoInfo(ms / 1000.0, minOf(w, h))
    } finally {
        r.release()
    }
}

fun thumbnail(video: File, atS: Double): Bitmap? {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(video.absolutePath)
        r.getScaledFrameAtTime((atS * 1_000_000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, 640)
    } catch (e: RuntimeException) {
        null // unreadable or still-recording file: the card just shows no picture
    } finally {
        r.release()
    }
}

/** Finds the rallies from the sound of the ball and saves them with the match. */
suspend fun analyseRallies(session: SessionDir, onProgress: (Float) -> Unit): List<Rally> =
    withContext(Dispatchers.Default) {
        val duration = videoInfo(session.video).durationS
        val rallies = AutoCut.rallies(AutoCut.hits(AutoCut.envelope(session.video, onProgress)), duration)
        session.writeRallies(rallies)
        rallies
    }

enum class VideoKind(val title: String) { HIGHLIGHTS("Highlights"), TOP_RALLIES("Top rallies") }

/**
 * Cuts the chosen rallies out of the match into one video, burns in the score where it is known,
 * and saves it to the Gallery ("Tennis Cut" album).
 */
suspend fun makeVideo(context: Context, session: SessionDir, kind: VideoKind, onProgress: (Int) -> Unit): Uri =
    withContext(Dispatchers.Main) {
        val setup = session.readSetup()
        val info = videoInfo(session.video)
        val rallies = session.readRallies() ?: error("The match has not been analysed yet.")
        val scored = scoreRallies(setup, rallies, session.readVerdicts(rallies))
        val chosen = when (kind) {
            VideoKind.HIGHLIGHTS -> scored
            VideoKind.TOP_RALLIES -> scored.sortedByDescending { it.rally.shots }.take(TOP_RALLY_COUNT).sortedBy { it.idx }
        }
        require(chosen.isNotEmpty()) { "No rallies were found in this recording." }

        val items = chosen.map { r ->
            val clip = MediaItem.Builder()
                .setUri(Uri.fromFile(session.video))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs((r.rally.start * 1000).toLong())
                        .setEndPositionMs((r.rally.end * 1000).toLong())
                        .build()
                )
                .build()
            val item = EditedMediaItem.Builder(clip)
            r.before?.let { before ->
                val overlay = BitmapOverlay.createStaticBitmapOverlay(
                    scoreboardBitmap(setup, before, info.shortSide),
                    StaticOverlaySettings.Builder()
                        .setOverlayFrameAnchor(-1f, 1f) // the board's top-left corner...
                        .setBackgroundFrameAnchor(-0.95f, 0.94f) // ...sits near the video's top-left corner
                        .build(),
                )
                item.setEffects(Effects(listOf(), listOf(OverlayEffect(ImmutableList.of<TextureOverlay>(overlay)))))
            }
            item.build()
        }
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(items).build()).build()
        val out = File(session.dir, "export.mp4").also { it.delete() }
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(EXPORT_BITRATE).build())
                    .build()
            )
            .build()

        coroutineScope {
            val progress = launch {
                val holder = ProgressHolder()
                while (true) {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                    delay(500)
                }
            }
            try {
                suspendCancellableCoroutine { cont ->
                    transformer.addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            cont.resume(Unit)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
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
            saveToGallery(context, out, "Tennis ${kind.title} ${setup.sessionId}.mp4").also { out.delete() }
        }
        when (kind) {
            VideoKind.HIGHLIGHTS -> session.highlightsUri = uri
            VideoKind.TOP_RALLIES -> session.topRalliesUri = uri
        }
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

/** TV-style score bug for the score before a point, sized for a frame whose short side is `shortSide`. */
fun scoreboardBitmap(setup: Setup, s: Score, shortSide: Int): Bitmap {
    val u = shortSide / 1080f
    val pad = 10 * u
    val rowH = 50 * u
    val ballW = 30 * u
    val nameW = 230 * u
    val colW = 44 * u
    val ptsW = 74 * u
    val sets = s.sets
    val showGames = !Tennis.isMatchTiebreak(s, setup.format)
    val labels = Tennis.pointLabels(s, setup.format)
    val serving = Tennis.server(s, setup.format)
    val cols = sets.size + if (showGames) 1 else 0

    val bodyW = pad + ballW + nameW + colW * cols + pad
    val width = bodyW + ptsW
    val height = 2 * rowH
    val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = 12 * u
    canvas.drawRoundRect(RectF(0f, 0f, width, height), radius, radius, fill(Color.argb(235, 11, 31, 58)))
    canvas.drawRoundRect(RectF(bodyW, 0f, width, height), radius, radius, fill(ACCENT))
    canvas.drawRect(RectF(bodyW, 0f, bodyW + radius, height), fill(ACCENT))
    canvas.drawRect(RectF(0f, rowH - u, bodyW, rowH + u), fill(Color.argb(60, 255, 255, 255)))

    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 34 * u
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    for (player in Player.entries) {
        val row = player.ordinal
        val centerY = rowH * (row + 0.5f)
        val baseline = centerY + text.textSize * 0.36f
        var x = pad
        if (player == serving) canvas.drawCircle(x + ballW / 2, centerY, 8 * u, fill(ACCENT))
        x += ballW
        text.textAlign = Paint.Align.LEFT
        text.color = Color.WHITE
        canvas.drawText(setup.name(player).take(14), x, baseline, text)
        x += nameW
        text.textAlign = Paint.Align.CENTER
        for (set in sets) {
            text.color = if (set[row] > set[1 - row]) Color.WHITE else Color.argb(150, 255, 255, 255)
            canvas.drawText("${set[row]}", x + colW / 2, baseline, text)
            x += colW
        }
        if (showGames) {
            text.color = Color.WHITE
            canvas.drawText("${s.games[row]}", x + colW / 2, baseline, text)
        }
        text.color = Color.rgb(11, 31, 58)
        canvas.drawText(labels[row], bodyW + ptsW / 2, baseline, text)
    }
    return bitmap
}

private val ACCENT = Color.rgb(215, 244, 74)

private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
