package com.tenniscut

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Home ----------

@Composable
fun HomeScreen(activity: MainActivity, onNewMatch: () -> Unit, onOpen: (SessionDir) -> Unit) {
    val sessions = remember {
        activity.sessionsRoot.listFiles().orEmpty()
            .filter { File(it, "setup.json").exists() && File(it, VIDEO_FILE).exists() }
            .sortedByDescending { it.name }
            .map { SessionDir(it) }
    }
    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.width(260.dp)) {
            Text("Tennis Cut", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Record a match, mark who wins each point, get a highlight video with the score on it.")
            Spacer(Modifier.height(24.dp))
            BigButton("New match", Color(0xFF2E7D32), onNewMatch)
            Spacer(Modifier.height(16.dp))
            Text("${"%.0f".format(freeGb(activity))} GB free · a 90-minute match needs about 15 GB",
                style = MaterialTheme.typography.bodySmall)
        }
        Column(Modifier.fillMaxSize()) {
            Text("Your matches", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            if (sessions.isEmpty()) Text("None yet.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions) { s ->
                    val setup = s.readSetup()
                    val presses = resolveMarkers(s.readMarkers())
                    Card(Modifier.fillMaxWidth().clickable { onOpen(s) }) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${setup.nameA} vs ${setup.nameB}", fontWeight = FontWeight.Bold)
                            Text("${prettyDate(s.dir.name)} · ${presses.size} points · ${scoreText(scoreAfter(setup, presses))}")
                        }
                    }
                }
            }
        }
    }
}

// ---------- Setup ----------

@Composable
fun SetupScreen(activity: MainActivity, onBack: () -> Unit, onStart: (SessionDir) -> Unit) {
    val prefs = remember { activity.getSharedPreferences("setup", Context.MODE_PRIVATE) }
    var nameA by remember { mutableStateOf(prefs.getString("nameA", "Me")!!) }
    var nameB by remember { mutableStateOf(prefs.getString("nameB", "Friend")!!) }
    var sets by remember { mutableStateOf(prefs.getInt("sets", 3)) }
    var finalSet by remember { mutableStateOf(FinalSet.fromJson(prefs.getString("finalSet", "match_tiebreak")!!)) }
    var noAd by remember { mutableStateOf(prefs.getBoolean("noAd", false)) }
    var firstServer by remember { mutableStateOf(Player.A) }
    var denied by remember { mutableStateOf(false) }

    fun start() {
        prefs.edit().putString("nameA", nameA).putString("nameB", nameB).putInt("sets", sets)
            .putString("finalSet", finalSet.json).putBoolean("noAd", noAd).apply()
        val id = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val dir = File(activity.sessionsRoot, id).apply { mkdirs() }
        val session = SessionDir(dir)
        session.writeSetup(Setup(id, nameA.trim().ifEmpty { "Me" }, nameB.trim().ifEmpty { "Friend" },
            MatchFormat(sets, noAd, finalSet), firstServer))
        session.writeMarkers(emptyList())
        onStart(session)
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.CAMERA] == true) start() else denied = true
    }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("New match", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(nameA, { nameA = it }, label = { Text("Your name") }, singleLine = true)
            OutlinedTextField(nameB, { nameB = it }, label = { Text("Friend's name") }, singleLine = true)
        }
        Choice("Match length", listOf("1 set" to 1, "Best of 3 sets" to 3, "Best of 5 sets" to 5), sets) { sets = it }
        if (sets > 1) {
            Choice("Deciding set", listOf("Tiebreak to 10" to FinalSet.MATCH_TIEBREAK, "Full set" to FinalSet.TIEBREAK),
                finalSet) { finalSet = it }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(noAd, { noAd = it })
            Spacer(Modifier.width(12.dp))
            Text("No-ad scoring (at deuce, the next point wins the game)")
        }
        Choice("Who serves first?", listOf(nameA to Player.A, nameB to Player.B), firstServer) { firstServer = it }
        if (denied) Text("Tennis Cut needs the camera to record. Allow it in the pop-up, or in Settings > Apps > Tennis Cut.",
            color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onBack) { Text("Back") }
            Button({
                val needed = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                if (needed.all { ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED }) start()
                else permissions.launch(needed.toTypedArray())
            }) { Text("Continue to camera") }
        }
    }
}

@Composable
private fun <T> Choice(label: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (text, value) ->
                FilterChip(selected == value, { onSelect(value) }, label = { Text(text) })
            }
        }
    }
}

// ---------- Record ----------

@Composable
fun RecordScreen(activity: MainActivity, session: SessionDir, onFinished: (SessionDir) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val setup = remember { session.readSetup() }
    val recorder = remember { MatchRecorder(context) }
    val beeper = remember { Beeper() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    var markers by remember { mutableStateOf(session.readMarkers()) }
    var lastAction by remember { mutableStateOf("") }
    var black by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(readStatus(context)) }
    var seconds by remember { mutableDoubleStateOf(0.0) }
    var confirmStop by remember { mutableStateOf(false) }

    fun mark(action: Clicker.Action, atNanos: Long, source: String) {
        if (!recorder.isRecording) return
        val t = recorder.videoTimeAt(atNanos)
        val marker = when (action) {
            Clicker.Action.A_WON -> Marker(t, Marker.POINT_WON, Player.A, source)
            Clicker.Action.B_WON -> Marker(t, Marker.POINT_WON, Player.B, source)
            Clicker.Action.UNDO -> Marker(t, Marker.UNDO, null, source)
        }
        markers = markers + marker
        session.writeMarkers(markers)
        beeper.confirm(action)
        lastAction = when (action) {
            Clicker.Action.A_WON -> "Point to ${setup.nameA}"
            Clicker.Action.B_WON -> "Point to ${setup.nameB}"
            Clicker.Action.UNDO -> "Last point undone"
        }
    }

    LaunchedEffect(Unit) {
        try {
            recorder.bind(owner, previewView)
        } catch (e: Exception) {
            recorder.error = "Could not open the camera: ${e.message}"
        }
    }
    DisposableEffect(Unit) {
        activity.clicker = Clicker(scope) { action, at -> mark(action, at, "clicker") }
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity.clicker = null
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setBrightness(activity, black = false)
            beeper.release()
        }
    }
    LaunchedEffect(black) { setBrightness(activity, black) }
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

    val score = scoreAfter(setup, resolveMarkers(markers))
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ previewView }, Modifier.fillMaxSize())

        Column(Modifier.align(Alignment.TopStart).padding(12.dp)) {
            ScorePanel(setup, score)
            Spacer(Modifier.height(8.dp))
            Text(statusLine(recorder, seconds, status), color = Color.White, fontSize = 14.sp,
                modifier = Modifier.background(Color(0x99000000)).padding(6.dp))
            if (lastAction.isNotEmpty()) {
                Text(lastAction, color = Color(0xFFFFE500), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color(0x99000000)).padding(6.dp))
            }
        }

        Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(230.dp).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            if (!recorder.isRecording) {
                Text("Fit the whole court in the picture, then start.", color = Color.White,
                    modifier = Modifier.background(Color(0x99000000)).padding(6.dp))
                BigButton("● Start recording", Color(0xFFC62828)) {
                    recorder.start(session.video) { if (session.video.exists()) onFinished(session) }
                }
            } else {
                BigButton("${setup.nameA} won", Color(0xFF2E7D32)) {
                    mark(Clicker.Action.A_WON, SystemClock.elapsedRealtimeNanos(), "screen")
                }
                BigButton("${setup.nameB} won", Color(0xFF1565C0)) {
                    mark(Clicker.Action.B_WON, SystemClock.elapsedRealtimeNanos(), "screen")
                }
                BigButton("Undo last point", Color(0xFF555555)) {
                    mark(Clicker.Action.UNDO, SystemClock.elapsedRealtimeNanos(), "screen")
                }
                BigButton("Black screen (saves battery)", Color(0xFF333333)) { black = true }
                BigButton("■ Stop match", Color(0xFFC62828)) { confirmStop = true }
            }
        }

        if (black) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { black = false }) {
                Text("Recording · ${scoreText(score)} · tap to wake", color = Color(0xFF444444),
                    modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop the match?") },
            text = { Text("Recording ends and the match is saved.") },
            confirmButton = { TextButton({ confirmStop = false; recorder.stop() }) { Text("Stop") } },
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

@Composable
private fun ScorePanel(setup: Setup, score: Score) {
    val labels = if (score.winner == null) Tennis.pointLabels(score, setup.format) else listOf("", "")
    val serving = Tennis.server(score, setup.format)
    val columns = score.sets + if (score.winner == null && !Tennis.isMatchTiebreak(score, setup.format)) listOf(score.games) else emptyList()
    Column(Modifier.background(Color(0xB3000000)).padding(10.dp)) {
        for (p in Player.entries) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (p == serving && score.winner == null) "●" else " ", color = Color(0xFFFFE500),
                    modifier = Modifier.width(20.dp))
                Text(setup.name(p).take(14), color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 20.sp, modifier = Modifier.width(150.dp))
                columns.forEach {
                    Text("${it[p.ordinal]}", color = Color.White, fontSize = 20.sp, modifier = Modifier.width(32.dp))
                }
                Text(labels[p.ordinal], color = Color(0xFFFFE500), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (score.winner != null) Text("Match won by ${setup.name(score.winner)}", color = Color(0xFFFFE500))
    }
}

private fun statusLine(recorder: MatchRecorder, seconds: Double, s: DeviceStatus): String {
    val rec = if (recorder.isRecording) "● REC ${clock(seconds)}" else "Ready"
    val fps = if (recorder.fps > 0) " · 1080p ${recorder.fps}fps" else ""
    val charging = if (s.charging) " (charging)" else ""
    return "$rec$fps · Battery ${s.batteryPct}%$charging · Phone heat: ${s.heat} · ${"%.0f".format(s.freeGb)} GB free"
}

private fun setBrightness(activity: MainActivity, black: Boolean) {
    val attrs = activity.window.attributes
    attrs.screenBrightness = if (black) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    activity.window.attributes = attrs
}

// ---------- Match ----------

@Composable
fun MatchScreen(session: SessionDir, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val scope = rememberCoroutineScope()
    val setup = remember { session.readSetup() }
    val points = remember {
        val duration = runCatching { videoInfo(session.video).durationS }.getOrDefault(Double.MAX_VALUE)
        pointsFrom(setup, resolveMarkers(session.readMarkers()), duration)
    }
    var progress by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var highlights by remember { mutableStateOf(session.highlightsUri) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(enabled = progress == null, onBack = onBack)

    fun make() {
        progress = "Starting" to 0
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        scope.launch {
            try {
                highlights = makeHighlights(context, session) { stage, pct -> progress = stage to pct }
            } catch (e: Exception) {
                error = "Making the video failed: ${e.message}"
            } finally {
                progress = null
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${setup.nameA} vs ${setup.nameB}", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            val final = points.lastOrNull()?.after ?: Score(gameServer = setup.firstServer)
            Text("${prettyDate(session.dir.name)} · ${points.size} points")
            Text(scoreText(final), fontSize = 22.sp)
            final.winner?.let { Text("Winner: ${setup.name(it)}") }
            Spacer(Modifier.height(8.dp))
            val current = progress
            when {
                current != null -> {
                    Text("${current.first}… ${current.second}%")
                    LinearProgressIndicator({ current.second / 100f }, Modifier.fillMaxWidth())
                    Text("Keep the app open on this screen. It takes a few minutes.",
                        style = MaterialTheme.typography.bodySmall)
                }
                highlights != null -> {
                    BigButton("▶ Watch highlights", Color(0xFF2E7D32)) {
                        open(context, Intent(Intent.ACTION_VIEW).setDataAndType(highlights, "video/mp4"))
                    }
                    BigButton("Share highlights", Color(0xFF1565C0)) {
                        val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, highlights)
                        open(context, Intent.createChooser(send, "Share highlights"))
                    }
                    Text("Also saved in your Gallery, in the \"Tennis Cut\" album.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(::make) { Text("Make it again") }
                }
                else -> {
                    if (points.isEmpty()) {
                        Text("No points were marked, so the app will find the rallies from the sound of the ball. " +
                            "The video will have no score on it.", style = MaterialTheme.typography.bodySmall)
                    }
                    BigButton("Make highlight video", Color(0xFF2E7D32), ::make)
                }
            }
            if (progress == null) {
                OutlinedButton({ confirmDelete = true }) { Text("Delete this match") }
            }
        }
        Column(Modifier.fillMaxSize()) {
            Text("Points", style = MaterialTheme.typography.titleLarge)
            LazyColumn {
                items(points) { p ->
                    Text("${p.idx + 1}.  ${setup.name(p.winner)} won  ·  score after: ${scoreText(p.after)} " +
                        (if (p.after.winner == null) Tennis.pointLabels(p.after, setup.format).joinToString("-") else "") +
                        "  ·  at ${clock(p.end)}",
                        modifier = Modifier.padding(vertical = 6.dp))
                    HorizontalDivider()
                }
            }
        }
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Problem") },
            text = { Text(message) },
            confirmButton = { TextButton({ error = null }) { Text("OK") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this match?") },
            text = { Text("The full recording is deleted to free space. A highlight video already in your Gallery is kept.") },
            confirmButton = { TextButton({ session.dir.deleteRecursively(); onBack() }) { Text("Delete") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

// ---------- Shared bits ----------

@Composable
private fun BigButton(text: String, color: Color, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = color)) {
        Text(text, fontSize = 18.sp, color = Color.White)
    }
}

data class DeviceStatus(val batteryPct: Int, val charging: Boolean, val heat: String, val freeGb: Double)

private fun readStatus(context: Context): DeviceStatus {
    val battery = context.getSystemService(BatteryManager::class.java)
    val heat = when (context.getSystemService(PowerManager::class.java).currentThermalStatus) {
        PowerManager.THERMAL_STATUS_NONE, PowerManager.THERMAL_STATUS_LIGHT -> "OK"
        PowerManager.THERMAL_STATUS_MODERATE -> "warm"
        PowerManager.THERMAL_STATUS_SEVERE -> "HOT, shade the phone"
        else -> "TOO HOT, may stop"
    }
    return DeviceStatus(
        batteryPct = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
        charging = battery.isCharging,
        heat = heat,
        freeGb = freeGb(context),
    )
}

private fun freeGb(context: Context) = (context.getExternalFilesDir(null)?.usableSpace ?: 0L) / 1e9

fun scoreText(s: Score): String =
    (s.sets + if (s.games != listOf(0, 0)) listOf(s.games) else emptyList())
        .joinToString("  ") { "${it[0]}-${it[1]}" }
        .ifEmpty { "0-0" }

private fun clock(seconds: Double): String {
    val total = seconds.toLong()
    return if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60)
    else "%d:%02d".format(total / 60, total % 60)
}

private fun prettyDate(sessionId: String): String = try {
    val date = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).parse(sessionId)!!
    SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(date)
} catch (e: Exception) {
    sessionId
}

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    } catch (e: ActivityNotFoundException) {
        // No video player or share target installed; nothing sensible to do.
    }
}
