package com.tenniscut

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.util.Range
import android.view.KeyEvent
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Camera preview + 1080p recording, and the clock that maps a button press to a time in the video. */
class MatchRecorder(private val context: Context) {
    var fps by mutableIntStateOf(0)
        private set
    var isRecording by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    private var videoCapture: VideoCapture<Recorder>? = null
    private var preview: Preview? = null
    private var recording: Recording? = null
    private var recordedNs = 0L
    private var recordedNsAt = 0L

    suspend fun bind(owner: LifecycleOwner, previewView: PreviewView, rotation: Int) {
        val provider = suspendCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
        }
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        val supports60 = provider.getCameraInfo(selector).supportedFrameRateRanges.any { it.lower == 60 && it.upper == 60 }
        val target = if (supports60) 60 else 30
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)))
            .build()
        val capture = VideoCapture.Builder(recorder)
            .setTargetFrameRate(Range(target, target))
            .setVideoStabilizationEnabled(false) // stabilisation warps the court geometry
            .setTargetRotation(rotation)
            .build()
        val preview = Preview.Builder().setTargetRotation(rotation).build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        provider.unbindAll()
        provider.bindToLifecycle(owner, selector, preview, capture)
        this.preview = preview
        videoCapture = capture
        fps = target
    }

    /** Follow the phone's orientation. A recording keeps the orientation it started with. */
    fun setRotation(rotation: Int) {
        preview?.targetRotation = rotation
        if (!isRecording) videoCapture?.targetRotation = rotation
    }

    @SuppressLint("MissingPermission") // audio is only enabled after the permission check
    fun start(file: File, onFinished: () -> Unit) {
        val capture = videoCapture ?: return
        var pending = capture.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        recordedNs = 0L
        recordedNsAt = SystemClock.elapsedRealtimeNanos()
        recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Status -> {
                    recordedNs = event.recordingStats.recordedDurationNanos
                    recordedNsAt = SystemClock.elapsedRealtimeNanos()
                }
                is VideoRecordEvent.Finalize -> {
                    isRecording = false
                    recording = null
                    if (event.hasError() && event.error != VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE) {
                        error = "Recording stopped unexpectedly (code ${event.error}). The video so far is saved."
                    }
                    onFinished()
                }
            }
        }
        isRecording = true
    }

    fun stop() {
        recording?.stop()
    }

    /** Seconds into the video at a moment given by SystemClock.elapsedRealtimeNanos(). */
    fun videoTimeAt(elapsedNanos: Long): Double = (recordedNs + (elapsedNanos - recordedNsAt)).coerceAtLeast(0) / 1e9
}

/**
 * Bluetooth shutter remotes send key presses (usually volume-up).
 * 1 press = player A won, 2 quick presses = player B, 3 presses or holding the button = undo.
 */
class Clicker(private val scope: CoroutineScope, private val onAction: (Action, Long) -> Unit) {
    enum class Action { A_WON, B_WON, UNDO }

    private var count = 0
    private var firstPressAt = 0L
    private var pending: Job? = null
    private var holdHandled = false

    fun onKeyDown(event: KeyEvent): Boolean {
        if (event.keyCode !in KEYS) return false
        if (event.repeatCount == 0) {
            if (count == 0) firstPressAt = SystemClock.elapsedRealtimeNanos()
            count++
            holdHandled = false
            pending?.cancel()
            pending = scope.launch {
                delay(MULTI_PRESS_WINDOW_MS)
                val action = when (count) {
                    1 -> Action.A_WON
                    2 -> Action.B_WON
                    else -> Action.UNDO
                }
                count = 0
                onAction(action, firstPressAt)
            }
        } else if (!holdHandled) {
            holdHandled = true
            pending?.cancel()
            count = 0
            onAction(Action.UNDO, SystemClock.elapsedRealtimeNanos())
        }
        return true
    }

    fun onKeyUp(event: KeyEvent): Boolean = event.keyCode in KEYS

    companion object {
        const val MULTI_PRESS_WINDOW_MS = 600L
        val KEYS = setOf(
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_CAMERA, KeyEvent.KEYCODE_SPACE,
        )
    }
}

/** Audible confirmation: you are on court, the phone is on the fence. */
class Beeper {
    private val tones = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)

    fun confirm(action: Clicker.Action) {
        when (action) {
            Clicker.Action.A_WON -> tones.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            Clicker.Action.B_WON -> tones.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            Clicker.Action.UNDO -> tones.startTone(ToneGenerator.TONE_PROP_NACK, 600)
        }
    }

    fun release() = tones.release()
}
