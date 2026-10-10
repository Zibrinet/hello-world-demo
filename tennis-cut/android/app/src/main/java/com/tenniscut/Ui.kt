package com.tenniscut

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Locale

// ---------- Look ----------

val Ball = Color(0xFFD7F44A)
val Navy = Color(0xFF0B1F3A)
val CourtBlue = Color(0xFF3D7BE0)
val Muted = Color(0xFF97A3B0)
val RecordRed = Color(0xFFE5484D)

private val colors = darkColorScheme(
    primary = Ball,
    onPrimary = Navy,
    secondary = CourtBlue,
    onSecondary = Color.White,
    background = Color(0xFF0C1117),
    onBackground = Color.White,
    surface = Color(0xFF0C1117),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A222C),
    onSurfaceVariant = Muted,
    surfaceContainer = Color(0xFF151C25),
    surfaceContainerHigh = Color(0xFF1C2530),
    surfaceContainerHighest = Color(0xFF242E3A),
    outline = Color(0xFF34404D),
    error = RecordRed,
)

@Composable
fun TennisTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colors, content = content)

// ---------- Building blocks ----------

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = Ball,
    contentColor: Color = Navy,
) {
    Button(
        onClick, modifier.height(56.dp), enabled = enabled, shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    OutlinedButton(onClick, modifier.height(48.dp), shape = RoundedCornerShape(14.dp)) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = Color(0xB3000000), textColor: Color = Color.White, dot: Color? = null) {
    Row(
        modifier.background(color, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun TopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 14.sp)
        }
        actions()
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
}

// ---------- Formatting ----------

fun scoreText(s: Score): String =
    (s.sets + if (s.games != listOf(0, 0)) listOf(s.games) else emptyList())
        .joinToString("  ") { "${it[0]}-${it[1]}" }
        .ifEmpty { "0-0" }

/** Sets, games and the points in the current game, e.g. "6-4  2-1  ·  30-15". */
fun scoreLine(setup: Setup, s: Score): String {
    if (s.winner != null) return scoreText(s)
    val pts = Tennis.pointLabels(s, setup.format)
    return if (s.points == listOf(0, 0)) scoreText(s) else "${scoreText(s)}  ·  ${pts[0]}-${pts[1]}"
}

fun clock(seconds: Double): String {
    val total = seconds.toLong()
    return if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60)
    else "%d:%02d".format(total / 60, total % 60)
}

fun minutes(seconds: Double) = "${(seconds / 60).toInt()} min"

fun prettyDate(sessionId: String): String = try {
    val date = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).parse(sessionId)!!
    SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()).format(date)
} catch (e: java.text.ParseException) {
    sessionId
}

// ---------- Phone ----------

data class DeviceStatus(val batteryPct: Int, val charging: Boolean, val hot: String?, val freeGb: Double)

fun readStatus(context: Context): DeviceStatus {
    val battery = context.getSystemService(BatteryManager::class.java)
    val hot = when (context.getSystemService(PowerManager::class.java).currentThermalStatus) {
        PowerManager.THERMAL_STATUS_NONE, PowerManager.THERMAL_STATUS_LIGHT -> null
        PowerManager.THERMAL_STATUS_MODERATE -> "Warm"
        PowerManager.THERMAL_STATUS_SEVERE -> "Hot · shade it"
        else -> "Too hot"
    }
    return DeviceStatus(
        battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY), battery.isCharging, hot, freeGb(context),
    )
}

fun freeGb(context: Context) = (context.getExternalFilesDir(null)?.usableSpace ?: 0L) / 1e9

fun watchVideo(context: Context, uri: Uri) =
    startSafely(context, Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4"))

fun shareVideo(context: Context, uri: Uri) =
    startSafely(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri), null))

private fun startSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    } catch (e: ActivityNotFoundException) {
        // No video player or share target installed; nothing sensible to do.
    }
}
