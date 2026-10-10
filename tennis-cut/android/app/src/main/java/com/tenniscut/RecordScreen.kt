package com.tenniscut

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

@Composable
fun RecordScreen(activity: MainActivity, session: SessionDir, onFinished: (SessionDir) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val setup = remember { session.readSetup() }
    val recorder = remember { MatchRecorder(context) }
    val beeper = remember { Beeper() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    var markers by remember { mutableStateOf(session.readMarkers()) }
    var toast by remember { mutableStateOf<String?>(null) }
    var screenOff by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(readStatus(context)) }
    var seconds by remember { mutableDoubleStateOf(0.0) }
    var confirmStop by remember { mutableStateOf(false) }

    // A Bluetooth clicker still works if you own one: 1 press you, 2 presses opponent, 3 or hold = undo.
    fun mark(action: Clicker.Action, atNanos: Long) {
        if (!recorder.isRecording) return
        val t = recorder.videoTimeAt(atNanos)
        markers = markers + when (action) {
            Clicker.Action.A_WON -> Marker(t, Marker.POINT_WON, Player.A, "clicker")
            Clicker.Action.B_WON -> Marker(t, Marker.POINT_WON, Player.B, "clicker")
            Clicker.Action.UNDO -> Marker(t, Marker.UNDO, null, "clicker")
        }
        session.writeMarkers(markers)
        beeper.confirm(action)
        toast = when (action) {
            Clicker.Action.A_WON -> "Point ${setup.nameA}"
            Clicker.Action.B_WON -> "Point ${setup.nameB}"
            Clicker.Action.UNDO -> "Undone"
        }
    }

    fun rotation() = previewView.display?.rotation ?: Surface.ROTATION_0

    LaunchedEffect(Unit) {
        try {
            recorder.bind(owner, previewView, rotation())
        } catch (e: Exception) {
            recorder.error = "Could not open the camera: ${e.message}"
        }
    }
    DisposableEffect(Unit) {
        activity.clicker = Clicker(scope) { action, at -> mark(action, at) }
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val displays = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) = recorder.setRotation(rotation())
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
        }
        displays.registerDisplayListener(listener, null)
        onDispose {
            displays.unregisterDisplayListener(listener)
            activity.clicker = null
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            setBrightness(activity, dim = false)
            beeper.release()
        }
    }
    LaunchedEffect(screenOff) { setBrightness(activity, screenOff) }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2500)
            toast = null
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            status = readStatus(context)
            if (recorder.isRecording) seconds = recorder.videoTimeAt(SystemClock.elapsedRealtimeNanos())
            delay(1000)
        }
    }
    BackHandler {
        if (recorder.isRecording) {
            confirmStop = true
        } else {
            session.dir.deleteRecursively() // nothing was recorded
            onCancel()
        }
    }

    fun startRecording() {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED // the video keeps this orientation
        recorder.start(session.video) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            if (session.video.exists()) onFinished(session)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ previewView }, Modifier.fillMaxSize())

        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
            // Top: back, recording state, phone status.
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter), verticalAlignment = Alignment.CenterVertically) {
                if (!recorder.isRecording) {
                    IconButton(
                        { session.dir.deleteRecursively(); onCancel() },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x99000000)),
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                    Spacer(Modifier.width(8.dp))
                }
                if (recorder.isRecording) Pill("REC  ${clock(seconds)}", dot = RecordRed)
                else if (recorder.fps > 0) Pill("1080p · ${recorder.fps} fps")
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    status.hot?.let { Pill(it, color = RecordRed) }
                    Pill("${status.batteryPct}%" + if (status.charging) " ⚡" else "")
                    Pill("${"%.0f".format(status.freeGb)} GB")
                }
            }

            if (!recorder.isRecording) {
                Text(
                    "Fit the whole court in the picture",
                    Modifier.align(Alignment.Center).background(Color(0x99000000), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    color = Color.White, textAlign = TextAlign.Center,
                )
            }
            if (markers.isNotEmpty()) {
                val score = remember(markers) {
                    resolveMarkers(markers).fold(Score(gameServer = setup.firstServer)) { s, m ->
                        if (s.winner == null) Tennis.applyPoint(s, setup.format, m.player!!) else s
                    }
                }
                Pill(scoreLine(setup, score), Modifier.align(Alignment.TopCenter).padding(top = 56.dp))
            }
            toast?.let {
                Pill(it, Modifier.align(Alignment.BottomCenter).padding(bottom = if (landscape) 8.dp else 120.dp),
                    color = Ball, textColor = Navy)
            }

            // Controls: right edge when sideways, bottom when upright.
            val controls: @Composable () -> Unit = {
                ShutterButton(recorder.isRecording) { if (recorder.isRecording) confirmStop = true else startRecording() }
            }
            val screenOffButton: @Composable () -> Unit = {
                if (recorder.isRecording) {
                    Text("Screen off", Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x99000000)).clickable { screenOff = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp), color = Color.White, fontSize = 14.sp)
                }
            }
            if (landscape) {
                Column(Modifier.align(Alignment.CenterEnd), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    controls()
                    screenOffButton()
                }
            } else {
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    controls()
                    screenOffButton()
                }
            }
        }

        if (screenOff) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { screenOff = false }) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Recording  ${clock(seconds)}", color = Color(0xFF555555), fontSize = 18.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap to wake", color = Color(0xFF3A3A3A), fontSize = 14.sp)
                }
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("End the match?") },
            text = { Text("Recording stops and the match is saved. Next, the app finds the rallies for you.") },
            confirmButton = { TextButton({ confirmStop = false; recorder.stop() }) { Text("End match") } },
            dismissButton = { TextButton({ confirmStop = false }) { Text("Keep recording") } },
        )
    }
    recorder.error?.let { message ->
        AlertDialog(
            onDismissRequest = { recorder.error = null },
            title = { Text("Problem") },
            text = { Text(message) },
            confirmButton = { TextButton({ recorder.error = null }) { Text("OK") } },
        )
    }
}

/** The camera-app style record button: a red dot that becomes a red square while recording. */
@Composable
private fun ShutterButton(recording: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(78.dp).clip(CircleShape).clickable(onClick = onClick).border(4.dp, Color.White, CircleShape).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (recording) Box(Modifier.size(28.dp).background(RecordRed, RoundedCornerShape(6.dp)))
        else Box(Modifier.fillMaxSize().background(RecordRed, CircleShape))
    }
}

private fun setBrightness(activity: MainActivity, dim: Boolean) {
    val attrs = activity.window.attributes
    attrs.screenBrightness = if (dim) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    activity.window.attributes = attrs
}
