package com.tenniscut

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** After the match: each rally plays on a loop, you tap who won it, the next one starts. */
@Composable
fun ScoringScreen(session: SessionDir, onDone: () -> Unit) {
    val context = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val setup = remember { session.readSetup() }
    val rallies = remember { session.readRallies().orEmpty() }
    var verdicts by remember { mutableStateOf(session.readVerdicts(rallies)) }
    var idx by remember { mutableIntStateOf(rallies.indices.firstOrNull { it !in verdicts } ?: 0) }
    var finished by remember { mutableStateOf(rallies.isEmpty()) }
    var fast by remember { mutableStateOf(false) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    BackHandler(onBack = onDone)

    LaunchedEffect(idx, finished) {
        if (finished) {
            player.pause()
            return@LaunchedEffect
        }
        val r = rallies[idx]
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(Uri.fromFile(session.video))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs((r.start * 1000).toLong())
                        .setEndPositionMs((r.end * 1000).toLong())
                        .build()
                )
                .build()
        )
        player.prepare()
        player.play()
    }
    LaunchedEffect(fast) { player.setPlaybackSpeed(if (fast) 2f else 1f) }

    val scored = remember(verdicts) { scoreRallies(setup, rallies, verdicts) }
    // Score going into this rally: the last scored point before it, if the chain is unbroken.
    val before = remember(scored, idx) {
        val prior = scored.lastOrNull { it.idx < idx }
        if (prior == null) Score(gameServer = setup.firstServer) else prior.after
    }

    fun decide(v: Verdict) {
        verdicts = verdicts + (idx to v)
        session.writeVerdicts(verdicts)
        val next = rallies.indices.firstOrNull { it > idx && it !in verdicts } ?: (idx + 1)
        if (next >= rallies.size) {
            val firstGap = rallies.indices.firstOrNull { it !in verdicts }
            if (firstGap != null) idx = firstGap else finished = true
        } else {
            idx = next
        }
    }

    val video: @Composable (Modifier) -> Unit = { modifier ->
        AndroidView(
            { PlayerView(it).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
            } },
            modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black),
        )
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        TopBar(
            if (finished) "All scored" else "Rally ${idx + 1} of ${rallies.size}",
            "${verdicts.size} of ${rallies.size} done",
            onDone,
        )
        LinearProgressIndicator({ verdicts.size / rallies.size.coerceAtLeast(1).toFloat() }, Modifier.fillMaxWidth())
        val panel: @Composable ColumnScope.() -> Unit = {
            if (finished) DonePanel(setup, scored, onDone) else DecisionPanel(setup, before, rallies[idx], verdicts[idx], fast,
                onVerdict = ::decide,
                onPrevious = { if (idx > 0) idx -= 1 },
                onReplay = { player.seekTo(0) },
                onSpeed = { fast = !fast })
        }
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                video(Modifier.weight(1.6f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), content = panel)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                video(Modifier.fillMaxWidth().weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = panel)
            }
        }
    }
}

@Composable
private fun ColumnScope.DecisionPanel(
    setup: Setup,
    before: Score?,
    rally: Rally,
    current: Verdict?,
    fast: Boolean,
    onVerdict: (Verdict) -> Unit,
    onPrevious: () -> Unit,
    onReplay: () -> Unit,
    onSpeed: () -> Unit,
) {
    if (before != null) {
        Text(scoreLine(setup, before), fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text("${setup.name(Tennis.server(before, setup.format))} serving · ~${rally.shots} shots", color = Muted, fontSize = 14.sp)
    } else {
        Text("~${rally.shots} shots", color = Muted, fontSize = 14.sp)
    }
    Text("Who won this point?", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton(setup.nameA, { onVerdict(Verdict.A_WON) }, Modifier.weight(1f).height(64.dp),
            icon = if (current == Verdict.A_WON) Icons.Default.Check else null)
        PrimaryButton(setup.nameB, { onVerdict(Verdict.B_WON) }, Modifier.weight(1f).height(64.dp),
            icon = if (current == Verdict.B_WON) Icons.Default.Check else null, color = CourtBlue, contentColor = Color.White)
    }
    SecondaryButton("Not a point (warm-up, ball pick-up…)", { onVerdict(Verdict.NOT_A_POINT) }, Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onPrevious) { Text("‹ Previous") }
        TextButton(onReplay) { Text("Replay") }
        TextButton(onSpeed) { Text(if (fast) "Normal speed" else "2× speed") }
    }
}

@Composable
private fun ColumnScope.DonePanel(setup: Setup, scored: List<ScoredRally>, onDone: () -> Unit) {
    val final = scored.lastOrNull()?.after
    Box(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Every point is scored", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            if (final != null) {
                Text(scoreText(final), fontSize = 20.sp)
                final.winner?.let { Text("${setup.name(it)} won the match", color = Ball) }
            }
            Text("Your highlight videos will now show the score, and match stats are ready.", color = Muted)
            PrimaryButton("Back to the match", onDone, Modifier.fillMaxWidth())
        }
    }
}
