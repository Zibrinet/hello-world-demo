package com.tenniscut

import android.graphics.Bitmap
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun MatchScreen(session: SessionDir, onBack: () -> Unit, onScore: () -> Unit) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val scope = rememberCoroutineScope()
    val setup = remember { session.readSetup() }
    val recorded = remember { runCatching { videoInfo(session.video).durationS }.getOrDefault(0.0) }
    var rallies by remember { mutableStateOf(session.readRallies()) }
    var analysing by remember { mutableStateOf<Float?>(null) }
    var exporting by remember { mutableStateOf<Pair<VideoKind, Int>?>(null) }
    var highlights by remember { mutableStateOf(session.highlightsUri) }
    var topRallies by remember { mutableStateOf(session.topRalliesUri) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    val scored = remember(rallies) { rallies?.let { scoreRallies(setup, it, session.readVerdicts(it)) } }
    val stats = remember(scored) { scored?.let { matchStats(setup, it) } }
    BackHandler(onBack = onBack)

    fun keepAwake(on: Boolean) {
        if (on) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    LaunchedEffect(Unit) { thumb = withContext(Dispatchers.IO) { thumbnail(session.video, 30.0) } }
    LaunchedEffect(Unit) {
        if (rallies != null) return@LaunchedEffect
        analysing = 0f
        keepAwake(true)
        try {
            rallies = analyseRallies(session) { analysing = it }
        } catch (e: Exception) {
            error = "Could not analyse the recording: ${e.message}"
        } finally {
            analysing = null
            keepAwake(false)
        }
    }

    fun export(kind: VideoKind) {
        exporting = kind to 0
        keepAwake(true)
        scope.launch {
            try {
                val uri = makeVideo(context, session, kind) { exporting = kind to it }
                if (kind == VideoKind.HIGHLIGHTS) highlights = uri else topRallies = uri
            } catch (e: Exception) {
                error = "Making the video failed: ${e.message}"
            } finally {
                exporting = null
                keepAwake(false)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val wide = maxWidth > 720.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TopBar("${setup.nameA} vs ${setup.nameB}", "${prettyDate(session.dir.name)} · ${minutes(recorded)} recorded", onBack)

            val summary: @Composable () -> Unit = { Summary(setup, thumb, recorded, rallies, scored, analysing) }
            val actions: @Composable () -> Unit = {
                if (scored != null && scored.isNotEmpty()) {
                    ScoringPanel(scored, onScore)
                    VideoPanel(VideoKind.HIGHLIGHTS, "Every rally back to back, dead time removed" +
                        if (scored.any { it.before != null }) ", with the score." else ".",
                        highlights, exporting, ::export)
                    VideoPanel(VideoKind.TOP_RALLIES, "Your $TOP_RALLY_COUNT longest rallies in one short clip, made for sharing.",
                        topRallies, exporting, ::export)
                }
            }
            val statsPanel: @Composable () -> Unit = { if (stats != null && stats.points > 0) StatsPanel(setup, stats) }

            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) { summary(); statsPanel() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) { actions() }
                }
            } else {
                summary()
                actions()
                statsPanel()
            }
            if (analysing == null && exporting == null) {
                TextButton({ confirmDelete = true }, Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Delete this match", color = Muted)
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
            text = { Text("The full recording is deleted to free space. Videos already in your Gallery are kept.") },
            confirmButton = { TextButton({ session.dir.deleteRecursively(); onBack() }) { Text("Delete") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Summary(setup: Setup, thumb: Bitmap?, recorded: Double, rallies: List<Rally>?, scored: List<ScoredRally>?, analysing: Float?) {
    Panel {
        Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f)) {
            FrameImage(thumb, Modifier.fillMaxSize())
            val final = scored?.takeIf { s -> s.isNotEmpty() && s.all { it.after != null } }?.last()?.after
            if (final != null) {
                Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    Pill(scoreText(final), color = Ball, textColor = Navy)
                    final.winner?.let {
                        Spacer(Modifier.height(6.dp))
                        Pill("${setup.name(it)} won")
                    }
                }
            }
        }
        when {
            analysing != null -> {
                Text("Finding the rallies…", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                LinearProgressIndicator({ analysing }, Modifier.fillMaxWidth())
                Text("Listening for the ball on the strings. Keep the app open; it takes a minute or two.",
                    color = Muted, fontSize = 14.sp)
            }
            rallies == null -> Text("This match hasn't been analysed.", color = Muted)
            rallies.isEmpty() -> Text("No rallies were found. Was the sound on, and the phone close enough to the court?",
                color = Muted)
            else -> {
                Text("${rallies.size} rallies", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text("${minutes(rallies.sumOf { it.playS })} of tennis in ${minutes(recorded)} recorded", color = Muted)
            }
        }
    }
}

@Composable
private fun ScoringPanel(scored: List<ScoredRally>, onScore: () -> Unit) {
    val total = scored.size
    val done = scored.count { it.verdict != null }
    Panel {
        Text("Score the points", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        when {
            done == 0 -> {
                Text("Watch each rally and tap who won it. About ${(total * 5 / 60.0).roundToInt().coerceAtLeast(1)} minutes. " +
                    "This puts the score on your videos and unlocks match stats.", color = Muted, fontSize = 14.sp)
                PrimaryButton("Start scoring", onScore, Modifier.fillMaxWidth(), icon = Icons.Default.PlayArrow)
            }
            done < total -> {
                LinearProgressIndicator({ done / total.toFloat() }, Modifier.fillMaxWidth())
                Text("$done of $total scored", color = Muted, fontSize = 14.sp)
                PrimaryButton("Continue scoring", onScore, Modifier.fillMaxWidth(), icon = Icons.Default.PlayArrow)
            }
            else -> {
                Text("All $total points scored.", color = Muted, fontSize = 14.sp)
                SecondaryButton("Review scoring", onScore, Modifier.fillMaxWidth(), icon = Icons.Default.Check)
            }
        }
    }
}

@Composable
private fun VideoPanel(kind: VideoKind, description: String, uri: Uri?, exporting: Pair<VideoKind, Int>?, onMake: (VideoKind) -> Unit) {
    val context = LocalContext.current
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(kind.title + " video", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (kind == VideoKind.TOP_RALLIES) Icon(Icons.Default.Star, null, tint = Ball)
        }
        Text(description, color = Muted, fontSize = 14.sp)
        val progress = exporting?.takeIf { it.first == kind }?.second
        when {
            progress != null -> {
                LinearProgressIndicator({ progress / 100f }, Modifier.fillMaxWidth())
                Text("Making the video… $progress%. Keep the app open.", color = Muted, fontSize = 14.sp)
            }
            uri != null -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Watch", { watchVideo(context, uri) }, Modifier.weight(1f), icon = Icons.Default.PlayArrow)
                    SecondaryButton("Share", { shareVideo(context, uri) }, Modifier.weight(1f).height(56.dp), icon = Icons.Default.Share)
                }
                TextButton({ onMake(kind) }, enabled = exporting == null) {
                    Icon(Icons.Default.Refresh, null, Modifier.padding(end = 6.dp))
                    Text("Make it again (e.g. after scoring more points)")
                }
            }
            else -> PrimaryButton("Make video", { onMake(kind) }, Modifier.fillMaxWidth(), enabled = exporting == null,
                color = CourtBlue, contentColor = Color.White)
        }
    }
}

@Composable
private fun StatsPanel(setup: Setup, s: MatchStats) {
    Panel {
        Text("Match stats", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text("From ${s.points} scored points. Shot counts are estimated from the sound, so treat them as close, not exact.",
            color = Muted, fontSize = 13.sp)
        Row {
            Text(setup.nameA, Modifier.weight(1f), color = Ball, fontWeight = FontWeight.Bold)
            Text(setup.nameB, Modifier.weight(1f), color = CourtBlue, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        }
        SplitBar(s.a.pointsWon, s.b.pointsWon)
        StatRow("Points won", "${s.a.pointsWon}", "${s.b.pointsWon}")
        StatRow("Won on serve", ratio(s.a.servePointsWon, s.a.servePoints), ratio(s.b.servePointsWon, s.b.servePoints))
        StatRow("Won on return", ratio(s.a.returnPointsWon, s.a.returnPoints), ratio(s.b.returnPointsWon, s.b.returnPoints))
        StatRow("Short rallies won (1-4 shots)", "${s.a.shortRalliesWon}", "${s.b.shortRalliesWon}")
        StatRow("Long rallies won (5+ shots)", "${s.a.longRalliesWon}", "${s.b.longRalliesWon}")
        Spacer(Modifier.height(2.dp))
        Text("Average rally ~${"%.1f".format(s.averageShots)} shots · longest ~${s.longestRally?.rally?.shots ?: 0} shots · " +
            "${minutes(s.playS)} of actual play", color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun SplitBar(a: Int, b: Int) {
    val total = (a + b).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
        if (a > 0) Box(Modifier.weight(a.toFloat() / total).fillMaxSize().background(Ball))
        if (b > 0) Box(Modifier.weight(b.toFloat() / total).fillMaxSize().background(CourtBlue))
        if (a + b == 0) Box(Modifier.weight(1f).fillMaxSize().background(Muted))
    }
}

@Composable
private fun StatRow(label: String, a: String, b: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(a, Modifier.width(76.dp), fontWeight = FontWeight.SemiBold)
        Text(label, Modifier.weight(1f), color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Text(b, Modifier.width(76.dp), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

private fun ratio(won: Int, of: Int) = if (of == 0) "-" else "$won/$of"
