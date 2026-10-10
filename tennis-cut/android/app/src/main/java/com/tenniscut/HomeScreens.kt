package com.tenniscut

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    LazyVerticalGrid(
        GridCells.Adaptive(300.dp),
        Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tennis Cut", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("Record. Score. Relive.", color = Muted)
                    }
                    Pill("${"%.0f".format(freeGb(activity))} GB free", color = MaterialTheme.colorScheme.surfaceContainerHigh)
                }
                PrimaryButton("New match", onNewMatch, Modifier.fillMaxWidth().widthIn(max = 520.dp), icon = Icons.Default.Add)
                if (sessions.isNotEmpty()) SectionLabel("Your matches")
            }
        }
        if (sessions.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Panel {
                    Text("No matches yet", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text("Mount your phone high behind the baseline, tap New match, and just play. " +
                        "Afterwards the app finds every rally for you.", color = Muted)
                }
            }
        }
        items(sessions, key = { it.dir.name }) { MatchCard(it) { onOpen(it) } }
    }
}

@Composable
private fun MatchCard(session: SessionDir, onClick: () -> Unit) {
    val setup = remember { session.readSetup() }
    val rallies = remember { session.readRallies() }
    val scored = remember { rallies?.let { scoreRallies(setup, it, session.readVerdicts(it)) } }
    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) { thumb = withContext(Dispatchers.IO) { thumbnail(session.video, 30.0) } }

    val status = when {
        scored == null -> "Ready to analyse"
        scored.any { it.verdict == null } -> "${scored.count { it.verdict == null }} points to score"
        else -> scored.lastOrNull()?.after?.let { scoreText(it) } ?: "Scored"
    }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(Navy)) {
            thumb?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Pill(status, Modifier.align(Alignment.BottomStart).padding(10.dp))
            if (session.highlightsUri != null) {
                Pill("Highlights ready", Modifier.align(Alignment.TopEnd).padding(10.dp), color = Ball, textColor = Navy)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("${setup.nameA} vs ${setup.nameB}", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(prettyDate(session.dir.name), color = Muted, fontSize = 14.sp)
        }
    }
}

// ---------- New match ----------

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
    val a = nameA.trim().ifEmpty { "Me" }
    val b = nameB.trim().ifEmpty { "Friend" }

    fun start() {
        prefs.edit().putString("nameA", a).putString("nameB", b).putInt("sets", sets)
            .putString("finalSet", finalSet.json).putBoolean("noAd", noAd).apply()
        val id = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val session = SessionDir(File(activity.sessionsRoot, id).apply { mkdirs() })
        session.writeSetup(Setup(id, a, b, MatchFormat(sets, noAd, finalSet), firstServer))
        session.writeMarkers(emptyList())
        onStart(session)
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.CAMERA] == true) start() else denied = true
    }
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            TopBar("New match", onBack = onBack)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(nameA, { nameA = it }, Modifier.weight(1f), label = { Text("You") }, singleLine = true)
                OutlinedTextField(nameB, { nameB = it }, Modifier.weight(1f), label = { Text("Opponent") }, singleLine = true)
            }
            Choice("Match", listOf("1 set" to 1, "Best of 3" to 3, "Best of 5" to 5), sets) { sets = it }
            if (sets > 1) {
                Choice("Deciding set", listOf("Tiebreak to 10" to FinalSet.MATCH_TIEBREAK, "Full set" to FinalSet.TIEBREAK),
                    finalSet) { finalSet = it }
            }
            Choice("Deuce", listOf("Advantage" to false, "No-ad (next point wins)" to true), noAd) { noAd = it }
            Choice("Serving first", listOf(a to Player.A, b to Player.B), firstServer) { firstServer = it }
            Panel {
                Text("Setting up the phone", fontWeight = FontWeight.SemiBold)
                Text("Mount it as high as you can behind the middle of the baseline, upright or sideways, " +
                    "with the whole court in view. Then just play: there is nothing to press during the match.",
                    color = Muted, fontSize = 14.sp)
            }
            if (denied) {
                Text("Tennis Cut needs the camera to record. Allow it in the pop-up, or in Settings > Apps > Tennis Cut.",
                    color = MaterialTheme.colorScheme.error)
            }
            PrimaryButton("Open camera", {
                val needed = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                if (needed.all { ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED }) start()
                else permissions.launch(needed.toTypedArray())
            }, Modifier.fillMaxWidth(), icon = Icons.Default.PlayArrow)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun <T> Choice(label: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (text, value) ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    label = { Text(text, maxLines = 1) },
                )
            }
        }
    }
}

/** Rounded thumbnail helper shared by screens that show a frame of the match. */
@Composable
fun FrameImage(bitmap: Bitmap?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Navy)) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
